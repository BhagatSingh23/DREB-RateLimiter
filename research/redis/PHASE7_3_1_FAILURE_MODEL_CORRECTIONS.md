# PHASE 7.3.1: RESTRICTED REDIS FAILURE MODEL CORRECTIONS

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.1 Workstream A  
**Repository Branch:** `research/systematic-improvement`  
**Starting Reference Commit:** `c4a857cd371dfb984a44ffee0684176303c84477`  

---

## 1. Executive Summary

This audit resolves the failure-model inconsistencies identified in Phase 7.3. We rigorously separate mathematical abstractions from the operational mechanics of the Redis engine. 

Specifically, this document:
1. Documents the exact Redis Cluster topology and replica allocation evaluated.
2. Demonstrates why standard Redis Cluster consensus **does not guarantee that the replica acknowledged by `WAIT` is the one promoted**.
3. Clarifies the precise role and limits of `cluster-replica-validity-factor`.
4. Discloses connection affinity constraints in the actual `JedisCluster` production adapter.
5. Replaces unqualified "if and only if" claims with sound sufficiency conditions.
6. Explicitly defines the failure domains (partitions, simultaneous crashes, replica drops) excluded from the model.

---

## 2. Redis Cluster Topology and Replica Allocation

The live cluster evaluated throughout Phases 7–7.3 comprises **6 Redis 7.4.11 nodes** organized into **3 primary-replica shards**:

| Shard Index | Primary Node | Primary Port | Hash Slots Allocated | Assigned Replica Node | Replica Port | Total Replicas per Primary |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Shard 1** | Node 1 | Port 7001 | `0 - 5460` (5,461 slots) | Node 6 | Port 7006 | **1** |
| **Shard 2** | Node 2 | Port 7002 | `5461 - 10922` (5,462 slots) | Node 4 | Port 7004 | **1** |
| **Shard 3** | Node 5 / 3 | Port 7005 / 7003 | `10923 - 16383` (5,461 slots) | Node 3 / 5 | Port 7003 / 7005 | **1** |

> [!IMPORTANT]
> **Replication Factor $N = 1$:** In this deployment, each primary possesses **exactly one replica**.  
> Under $N = 1$, if the primary crashes, the single assigned replica is the *only* candidate available for promotion.  
> However, in general production topologies where $N \ge 2$, `WAIT 1 1000` confirms only that *at least one* replica acknowledged the write, but does **not** ensure that the specific acknowledging replica will be the one promoted by cluster consensus.

---

## 3. Dissecting the "Highest-Offset Election" Claim

In Phase 7.3, highest-offset election was cited as a core pillar of failover state preservation. An independent review of the Redis Cluster specification reveals that this election ordering is **probabilistic, not a deterministic consensus guarantee**:

1. **The Offset Delay Mechanism:**
   When a primary fails, each replica computes an election delay:
   $$\text{delay} = 500\,\text{ms} + \text{random}(0, 500\,\text{ms}) + (\text{master\_offset} - \text{replica\_offset}) \times \text{offset\_rank\_delay}$$
   Replicas with higher offsets (closer to the primary) attempt to initiate the election earlier than lagged replicas.
2. **Why This Fails as a Formal Guarantee:**
   - **Network Jitter & Packet Loss:** If the higher-offset replica's vote request (`FAILOVER_AUTH_REQUEST`) experiences network packet loss or asymmetric routing delays, a lagged replica's vote request may reach a majority of masters first.
   - **Split Voting:** If multiple replicas compete, vote splitting can result in a lagged replica winning authorization in a subsequent election epoch.
   - **Standard Redis Is Not Raft:** Unlike Raft or Paxos, where leaders cannot be elected if their log is behind any voter, Redis Cluster masters grant votes based solely on config epoch, not log completeness.
3. **Verdict:** Highest-offset promotion is an **`[EMPIRICALLY_OBSERVED_OPTIMIZATION]`**, NOT an enforced consensus guarantee.

---

## 4. Role and Limits of `cluster-replica-validity-factor`

Redis provides the configuration parameter `cluster-replica-validity-factor` (default: 10):
- **What It Does:** Disqualifies a replica from attempting an election if it has been disconnected from its master for more than:
  $$t_{\text{disconnection\_limit}} = \text{node-timeout} \times \text{cluster-replica-validity-factor}$$
  For `node-timeout = 15s` and factor = 10, any replica disconnected for $> 150\,\text{s}$ is barred from failover.
- **What It Does NOT Do:**
  - It does **not** compare replication offsets between connected replicas.
  - It does **not** prevent a connected replica that is $100\,\text{ms}$ behind from winning an election over a replica that is $1\,\text{ms}$ behind.
  - Setting this factor to 0 permits *any* replica to attempt election regardless of disconnection duration.

---

## 5. Connection Affinity in `ProductionRedisRca70Limiter`

Workstream A specifically investigates connection affinity in the actual production adapter under `src/main/java/com/dreb/ratelimit/redis/`:

1. **Current Production Adapter Architecture:**
   In `ProductionRedisRca70Limiter.java`, requests are dispatched via:
   ```java
   connectionProvider.evalLua(scriptSha, RCA70_PRODUCTION_LUA, keys, args);
   ```
   When configured with `RedisConnectionProvider.Cluster`, the provider calls:
   ```java
   jedisCluster.evalsha(scriptSha, keys, args);
   ```
2. **The Connection Affinity Finding:**
   - `JedisCluster` internally borrows a connection from the node pool, executes `evalsha`, and **immediately returns the connection to the pool**.
   - `JedisCluster` has **no public `waitReplicas` API**.
   - The production adapter currently **does not execute `WAIT` during cluster execution**.
   - Synchronous `WAIT` verification was executed in test suites using connection-pinned `JedisPool` standalone checkouts.
3. **Operational Consequence:** The production `JedisCluster` adapter currently runs in **asynchronous replication mode**. Connection-pinned `WAIT` support requires a custom connection provider checkout mechanism.

---

## 6. Removal of the "If and Only If" Claim

Phase 7.3 asserted that G1 is preserved "IF AND ONLY IF" model $\mathcal{M}_{\text{G1-Valid}}$ is satisfied:
- **Logical Error:** "If and only if" asserts both **sufficiency** ($\mathcal{M} \implies G_1$) and **necessity** ($G_1 \implies \mathcal{M}$).
- While $\mathcal{M}$ establishes sufficiency for the tested single-crash scenario, it is **not necessary**—for example, if a client does not burst immediately following failover, G1 is preserved even under un-replicated failover.
- **Correction:** The biconditional "if and only if" is formally retracted and replaced with:
  > *"Model $\mathcal{M}_{\text{G1-Valid}}$ provides a set of **sufficient operational conditions** under which single-master failover does not violate the replication-protected burst bound."*

---

## 7. Explicit Exclusions from the Failure Model

To ensure scientific honesty, the following failure domains are explicitly declared **OUT OF SCOPE / EXCLUDED**:

1. **Network Partitions with Split-Brain Writes:** If a primary is partitioned on the minority side of a cluster, Redis Cluster permits the minority primary to accept writes for `cluster-node-timeout` (15s). These writes are irrevocably lost upon cluster re-merge. Such partitions are excluded.
2. **Simultaneous Multi-Node Power Failure:** A datacenter rack outage simultaneously halting primary and replica before disk fsync is excluded.
3. **Cascading Replica Crashes:** If the acknowledging replica crashes before completing promotion, state recovery is not guaranteed.
4. **General Multi-Replica Topologies ($N \ge 2$):** Topologies with multiple replicas per primary are excluded from election-preservation claims unless external fencing (e.g. Raft proxy) is deployed.
