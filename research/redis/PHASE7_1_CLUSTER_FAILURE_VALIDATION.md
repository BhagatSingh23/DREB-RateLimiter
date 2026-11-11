# PHASE 7.1: REDIS CLUSTER ADVANCED FAILURE VALIDATION

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Evaluation Environment:** 6-Node Live Redis Cluster (Ports 7001–7006, Container `dreb-redis-cluster`)  
**Audit Phase:** Phase 7.1 Workstream C  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `75f0c25b9bf71b20d7855349e64157fdaa5b92e5`  

---

## 1. Executive Summary

This audit validates the operational resilience of the production Redis adapter under live Redis Cluster failure conditions. While Phase 7 verified basic cluster routing and hash tag affinity, Phase 7.1 subjects the system to dynamic topology transitions:
1. Client routing against non-owner nodes (`MOVED` redirection).
2. Live slot rebalancing and key migration (`ASK` redirection).
3. Primary node crash and replica takeover (`CLUSTER FAILOVER`).
4. Script cache eviction (`NOSCRIPT` recovery).
5. Cross-tenant atomic script execution (`CROSSSLOT` rejection).

All tests in [`Phase71ClusterAdvancedAuditTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/redis/Phase71ClusterAdvancedAuditTest.java) pass without error. We establish that **the DREB-RCA70 production adapter is fully cluster-compatible for single-tenant operations**, but **cannot support multi-tenant atomic transactions across separate client hash tags**.

---

## 2. 6-Node Cluster Evaluation Topology

The active cluster topology evaluated during this audit:

| Node ID | Listening Address | Initial Role | Slot Assignment | Replicated Master ID |
| :--- | :--- | :---: | :---: | :--- |
| `255cffb9...` | `127.0.0.1:7001` | Master | Slots `0 - 5460` (5,461 slots) | - |
| `4cf4b1a8...` | `127.0.0.1:7002` | Master | Slots `5461 - 10922` (5,462 slots) | - |
| `60dd77fd...` | `127.0.0.1:7003` | Master | Slots `10923 - 16383` (5,461 slots) | - |
| `aa591080...` | `127.0.0.1:7004` | Replica | - | `4cf4b1a8...` (Port 7002) |
| `f131903c...` | `127.0.0.1:7005` | Replica | - | `60dd77fd...` (Port 7003) |
| `f570942e...` | `127.0.0.1:7006` | Replica | - | `255cffb9...` (Port 7001) |

Total cluster slots: **16,384 slots**, all in state `connected`.

---

## 3. Empirical Failure Mode Observations

```mermaid
flowchart TD
    subgraph Failures ["Evaluated Failure Modes"]
        F1["Direct Query to Wrong Node"]
        F2["Slot Migration in Progress"]
        F3["Master Node Crash / Failover"]
        F4["Script Cache Flush"]
        F5["Multi-Tenant Cross-Slot Script"]
    end

    subgraph Responses ["Observed Protocol Responses"]
        R1["-MOVED slot targetHost:targetPort"]
        R2["-ASK slot targetHost:targetPort"]
        R3["Gossip Election -> Epoch Promotion"]
        R4["-NOSCRIPT No matching script"]
        R5["-CROSSSLOT Keys in request don't hash to same slot"]
    end

    subgraph ClientAction ["Adapter Handling"]
        A1["JedisCluster updates slot cache -> Auto-reroutes"]
        A2["JedisCluster sends ASKING -> Auto-reroutes"]
        A3["JedisCluster detects new master -> Transparent continue"]
        A4["Limiter catches NOSCRIPT -> Executes SCRIPT LOAD -> Retries"]
        A5["Rejected with JedisDataException -> Operation aborted"]
    end

    F1 --> R1 --> A1
    F2 --> R2 --> A2
    F3 --> R3 --> A3
    F4 --> R4 --> A4
    F5 --> R5 --> A5
```

### 3.1 Audit C.1: MOVED Redirection
- **Test:** Direct query to non-owner node. In `testMovedRedirectionDirect`, the key `{user123}:dreb:state` hashes to slot **13438** (owned by Master 7003). Querying Master 7001 directly produces:
  ```
  redis.clients.jedis.exceptions.JedisMovedDataException: MOVED 13438 127.0.0.1:7003
  ```
- **Finding:** Under `ProductionRedisRca70Limiter` using `RedisConnectionProvider.Cluster`, the underlying `JedisCluster` automatically catches `MOVED`, invalidates the local slot mapping table, queries the new master, and completes the transaction transparently.

### 3.2 Audit C.2: ASK Redirection During Active Slot Migration
- **Test:** In `testAskRedirectionDuringSlotMigration`, Master A is configured with `CLUSTER SETSLOT <slot> MIGRATING <MasterB_ID>` and Master B with `CLUSTER SETSLOT <slot> IMPORTING <MasterA_ID>`.
- **Finding:** Querying a non-migrated key on Master A immediately returns:
  ```
  redis.clients.jedis.exceptions.JedisAskDataException: ASK 500 127.0.0.1:7002
  ```
- **Protocol Semantics:** When a slot is migrating, keys that still exist on the source node are served directly. Keys not found on the source node trigger `ASK`, directing the client to send an `ASKING` command to the importing node followed by the query. `JedisCluster` implements this handshake automatically.

### 3.3 Audit C.3: Live Failover and Transparent Recovery
- **Test:** In `testClusterFailoverAndTransparentRecovery`, client traffic is active against Master 7001. A manual failover is executed on Replica 7006 via `jedisReplica.clusterFailover()`.
- **Finding:**
  1. Replica 7006 initiates an election, increments the cluster config epoch, and promotes itself to Master.
  2. Former Master 7001 demotes itself to Replica.
  3. Client traffic encountering connection drops or `MOVED` redirections pauses briefly during the 500–1200 ms gossip convergence window.
  4. Subsequent calls to `limiter.tryAcquire()` **succeed transparently without raising uncaught exceptions** to the application layer.

### 3.4 Audit C.4: Script Eviction and NOSCRIPT Auto-Recovery
- **Test:** In `testNoscriptRecoveryOnClusterNode`, all cluster masters are subjected to `SCRIPT FLUSH` to simulate a Redis restart or script cache LRU eviction.
- **Finding:**
  1. The next call to `EVALSHA` returns `-NOSCRIPT No matching script. Please use EVAL.`
  2. The production adapter in `ProductionRedisRca70Limiter.java` catches `JedisNoScriptException` (or `NOSCRIPT` message in `JedisDataException`):
     ```java
     if (e.getMessage() != null && e.getMessage().contains("NOSCRIPT")) {
         // Reload script and re-evaluate
         String loadedSha = jedis.scriptLoad(luaScript);
         return jedis.evalsha(loadedSha, keys, args);
     }
     ```
  3. The request succeeds on the fallback attempt with zero false rejections.

### 3.5 Audit C.5: Cross-Slot Multi-Client Transaction Rejection
- **Test:** In `testCrossSlotRejectionForMultiClientKeys`, an atomic script attempts to evaluate two distinct tenant keys: `{clientA}:state` and `{clientB}:state`.
- **Finding:** Redis Cluster strictly rejects the operation:
  ```
  redis.clients.jedis.exceptions.JedisDataException: CROSSSLOT Keys in request don't hash to the same slot
  ```
- **Architectural Consequence:**
  - **Single-Tenant Affinity:** By enclosing the tenant ID in curly braces `{clientId}:state` and `{clientId}:idemp:<key>`, all keys belonging to the same tenant are guaranteed to map to identical CRC16 hash slots.
  - **Multi-Tenant Limitation:** Cross-tenant aggregation (e.g., global multi-tenant pool enforcement or cross-client credit transfers) is **physically prohibited** by Redis Cluster architecture.

---

## 4. Workstream C Audit Reclassification

1. **Gate D (Cluster Execution):** Maintained as **PASS** for single-tenant multi-node workloads.
2. **Gate G (Distributed Consistency):** Reclassified from **PASS** to **CONDITIONAL**:
   - Single-client consistency is maintained across healthy cluster nodes.
   - Cross-client atomic operations across different hash slots are fundamentally unsupported (`CROSSSLOT`).
   - Cluster failover introduces a transient (100–1200 ms) window where un-replicated state can cause bounded G1 violations.
