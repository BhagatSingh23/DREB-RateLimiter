# PHASE 9.1: MEMORY ACCOUNTING RECONCILIATION AND FOOTPRINT AUDIT

**Audit Date:** November 2026  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `c8df6d4`  
**Auditor:** Independent Red-Team Review  

---

## 1. Executive Summary

This audit reconciles the apparent contradiction between historical memory measurements in Phase 5 and the reported figures in the Phase 9 manuscript:
- **Historical Phase 5 Measurement:** Retained Java heap of **$150.6 - 165.3\,\text{bytes per client}$** in `ConcurrentHashMap<String, ClientState>` ([`PHASE5_1_MEMORY_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/PHASE5_1_MEMORY_AUDIT.md)).
- **Phase 9 Manuscript Statement:** Reported Java retained heap of **$56\,\text{bytes per client}$** (`DREB_RCA70_MANUSCRIPT_DRAFT.md`, Section 8.1, Table 5).
- **Redis Memory Measurements:** Historical Redis state hash of **$192 - 216\,\text{bytes}$**, and Redis idempotency record of **$176\,\text{bytes}$**.

### Resolution
The discrepancy stems from a **conflation of architectural measurement scopes**:
- **$56\,\text{bytes}$** is the **shallow instance size of an isolated `ClientState` Java object**, excluding collection overhead, map entry nodes, and client key strings.
- **$150.6 - 165.3\,\text{bytes}$** is the **true incremental retained heap footprint per client** when stored inside the gateway's active state collection (`ConcurrentHashMap<String, ClientState>`).
- Calling 56 bytes the "retained heap footprint" in the manuscript draft was an error of terminology.

This document formally dissects the memory hierarchy across logical, JVM, and Redis layers, and recalculates enterprise 1,000,000-client capacity planning.

---

## 2. Java Memory Dissection: Isolated Object vs. Retained Collection

### 2.1 Layer 1: Primitive Payload (40 Bytes)
As defined in [`ClientState.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/ClientState.java):
```java
public double burstTokens;      // 8 bytes (IEEE 754 float64)
public double sustainedTokens;  // 8 bytes (IEEE 754 float64)
public double idleCredit;       // 8 bytes (IEEE 754 float64)
public double abuseScore;       // 8 bytes (IEEE 754 float64)
public double lastRefillTs;     // 8 bytes (IEEE 754 float64)
```
- Total raw numeric payload: $5 \times 8\,\text{bytes} = \mathbf{40\,\text{bytes}}$.

### 2.2 Layer 2: Standalone Shallow Object Size (56 Bytes)
Under OpenJDK 64-bit JVM with compressed OOPs (`-XX:+UseCompressedOops`, active by default for heaps $< 32\,\text{GB}$):
- **Object Header:** 12 bytes (8-byte `markWord` + 4-byte compressed `klass` pointer).
- **Primitive Double Fields:** 40 bytes ($5 \times 8\,\text{bytes}$).
- **8-Byte Word Alignment Padding:** 4 bytes.
- **Total Shallow Instance Size:** $12 + 40 + 4 = \mathbf{56\,\text{bytes}}$.

### 2.3 Layer 3: Incremental Retained Heap in Gateway Store (150.6–165.3 Bytes)
In production, [`InMemoryStateStore.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/InMemoryStateStore.java) maps client identifiers to state via `ConcurrentHashMap<String, ClientState>`. Each stored client incurs three additional memory components:
1. **Map Entry Node (`ConcurrentHashMap.Node`):**
   - Header: 12 bytes
   - Hash (`int`): 4 bytes
   - Key pointer (`String`): 4 bytes
   - Value pointer (`ClientState`): 4 bytes
   - Next pointer: 4 bytes
   - Alignment padding: 4 bytes
   - Total Node Size: **32 bytes**.
2. **Client Key String (`java.lang.String`):**
   - Standard 16-character alphanumeric key (e.g., `"client_user_1001"`):
   - `String` instance: 12-byte header + 4-byte hash + 4-byte byte array ref + 4-byte flags/padding = 24 bytes.
   - Backing byte array (`byte[]`): 12-byte header + 4-byte length + 16-byte UTF-8/Latin1 payload = 32 bytes.
   - Total Key Size: **56 bytes**.
3. **Hash Table Bucket Slot:**
   - Table array slot pointer: 4 bytes. Amortized at load factor 0.75: $\approx \mathbf{5.33\,\text{bytes}}$.

$$\text{Retained Heap per Client} = 56\,(\text{State Object}) + 32\,(\text{Node}) + 56\,(\text{Key String}) + 5.33\,(\text{Bucket}) = \mathbf{149.33\,\text{bytes}}$$

Empirical measurements in Phase 5.1 across 1,000 to 100,000 clients yielded:
- $N = 1,000$: **$150.6\,\text{bytes / client}$**
- $N = 10,000$: **$150.6\,\text{bytes / client}$**
- $N = 100,000$: **$165.3\,\text{bytes / client}$** (increase due to table resizing array allocation).

---

## 3. Distributed Redis 7.4.11 Memory Dissection

In Redis Cluster, state and idempotency records are stored using hash-tag affinity (`{clientId}`):

### 3.1 Redis Client State Hash (`{clientId}:state`)
- Stored as a compact Redis Hash containing five fields: `b`, `s`, `i`, `k`, `t`.
- Encoded as a Redis `listpack` (under default `hash-max-listpack-entries = 128`):
  - Listpack payload: 5 key-value pairs $\approx 64\,\text{bytes}$.
  - Redis object structure (`robj`): 16 bytes.
  - Dict entry in Redis keyspace: 24 bytes (dictEntry) + 8-byte key SDS string + hash table bucket slot.
  - **Direct `MEMORY USAGE`:** **192 bytes**.
  - **Incremental keyspace consumption:** **$202.49 - 216\,\text{bytes}$** per key.

### 3.2 Redis Idempotency Record (`{clientId}:idemp:{reqId}`)
- Stored as a Redis String with 60-second TTL:
  - Value: JSON/compact string containing status (`ADMIT`/`REJECT`), tokens, and payload hash $\approx 48\,\text{bytes}$.
  - TTL overhead: Entry in Redis `expires` dict (dictEntry + pointer) = 32 bytes.
  - Redis object structure: 16 bytes.
  - SDS string overhead + keyspace dictEntry: $\approx 80\,\text{bytes}$.
  - **Direct `MEMORY USAGE`:** **176 bytes per active idempotency record**.

---

## 4. One-Million-Client Capacity Planning Model

Table 1 summarizes the memory footprint for sizing an enterprise API gateway cluster handling 1,000,000 active clients.

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: Complete Memory Footprint Breakdown across Storage Layers (1,000,000 Clients).                 |
+------------------------------------+-----------------------+--------------------+-----------------------+
| Architectural Layer                | Per-Client Footprint  | 1M Clients Total   | Measurement Scope     |
+------------------------------------+-----------------------+--------------------+-----------------------+
| 1. Logical Primitive Payload       | 40.0 bytes            | 40.0 MB            | Raw IEEE 754 doubles  |
| 2. Shallow Java Object             | 56.0 bytes            | 56.0 MB            | Isolated ClientState  |
| 3. Retained In-Memory Java Heap    | 150.6 - 165.3 bytes   | 150.6 - 165.3 MB   | ConcurrentHashMap     |
| 4. Distributed Redis State Hash    | 202.5 - 216.0 bytes   | 202.5 - 216.0 MB   | Redis keyspace hash   |
| 5. Redis Idempotency Record (TTL)  | 176.0 bytes           | 176.0 MB / 1M reqs | Per active in-flight  |
| 6. Total Redis Cluster (State+Idm) | 378.5 - 392.0 bytes   | 378.5 - 392.0 MB   | 1M clients + 1M idemp |
+------------------------------------+-----------------------+--------------------+-----------------------+
```

### Key Takeaways for Deployment
1. **In-Memory Java Gateway:** Sizing a gateway for 1,000,000 concurrent clients requires provisioned heap of **165 MB**, comfortably fitting within standard JVM microservice footprints (e.g., 512 MB – 1 GB containers).
2. **Distributed Redis Cluster:** Managing 1,000,000 active client states requires **~216 MB** of Redis memory. If the gateway also maintains 1,000,000 concurrent active idempotency records during peak burst windows (60s TTL), total Redis memory is **~392 MB**, evenly sharded across the 3 primary nodes (~131 MB per node).

---

## 5. Required Manuscript Corrections

1. **Section 8.1 & Table 5:**
   - Update text: Clarify that **56 bytes** is the *shallow instance size* of the `ClientState` object.
   - Report the full retained heap: **$150.6 - 165.3\,\text{bytes per client}$** when stored in `ConcurrentHashMap`.
2. **Redis Memory Reporting:**
   - Explicitly separate the persistent state hash (**216 bytes**) from the transient 60s idempotency record (**176 bytes**).
   - Report the combined peak footprint (**~392 bytes/client**) for systems operating with end-to-end idempotency protection.
