# PHASE 9.1: LATENCY AND PERFORMANCE AUDIT ACROSS MEASUREMENT SCOPES

**Audit Date:** November 2026  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `c8df6d4`  
**Auditor:** Independent Red-Team Review  

---

## 1. Executive Summary

This audit independently verifies the four performance measurement domains presented in the DREB-RCA70 manuscript, documenting sample sizes, warm-up intervals, measurement boundaries, percentile calculations, and hardware configurations.

### Key Audit Findings
1. **Scope D (`WAIT 1` Synchronous Mode) is NOT Sub-Millisecond:**
   - In synchronous replication mode (`WAIT 1 1000`), the 99th percentile tail latency is **$1.301\,\text{ms}$** ($> 1.0\,\text{ms}$).
   - In `DREB_RCA70_MANUSCRIPT_DRAFT.md`, the abstract broadly claimed "sub-millisecond execution over Redis Cluster" without qualifying that this holds strictly for **asynchronous mode** ($0.259\,\text{ms}$ P50, $0.667\,\text{ms}$ P99), whereas synchronous replication durability requires **$1.301\,\text{ms}$** at P99.
   - The manuscript must explicitly distinguish asynchronous sub-millisecond execution from synchronous replication durability.
2. **The +95.1% P99 Penalty is Verified:**
   - Asynchronous Redis network P99: $0.667\,\text{ms}$.
   - Synchronous `WAIT 1` Redis network P99: $1.301\,\text{ms}$.
   - Penalty calculation: $(1.301 - 0.667) / 0.667 = 0.634 / 0.667 = +95.05\% \approx \mathbf{+95.1\%}$.
3. **Cross-Boundary Comparisons Must Be Strictly Scoped:**
   - Directly comparing in-memory Java nanoseconds (42 ns) to Redis distributed milliseconds (0.259 ms) is comparing CPU cache accesses to network socket transitions. These domains must remain clearly compartmentalized into distinct architectural tiers.

---

## 2. Independent Dissection of the Four Measurement Domains

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: Independent Specifications across the Four Performance Measurement Domains.                   |
+--------------------------+---------------------+--------------------+-------------------+---------------+
| Parameter                | Scope A: In-Memory  | Scope B: Redis Lua | Scope C: Async Net| Scope D: Sync |
|                          | Java Dispatch       | Execution Engine   | Round-Trip (RTT)  | WAIT 1 RTT    |
+--------------------------+---------------------+--------------------+-------------------+---------------+
| **Measurement Boundary** | JVM method call     | Redis Lua script   | Gateway -> TCP -> | Gateway -> TCP|
|                          | `limiter.allow(c)`  | execution duration | Redis -> Response | -> EVAL -> W1 |
| **Timer Source**         | `System.nanoTime()` | Redis server clock | Client-side nano  | Client-side   |
|                          | (via JMH harness)   | (`redis.call(TIME)`| clock in Jedis    | nano in Jedis |
| **Sample Size**          | 1,500 benchmark runs| 10,000 script runs | 10,000 iterations | 10,000 iters  |
| **Warm-Up Iterations**   | 10 warm-up batches  | 1,000 warm-up iters| 1,000 warm-up     | 1,000 warm-up |
| **Concurrency Levels**   | 1 thread (CPU-bound)| 1-64 Redis clients | 1-64 Jedis threads| 1-64 threads  |
| **Percentile Method**    | Nearest-rank / JMH  | Empirical sorted   | Empirical sorted  | Empirical sort|
| **P50 Latency**          | **0.000042 ms (42ns)**| **0.082 ms (82 us)**| **0.259 ms (259 us)**| **0.348 ms**|
| **P90 Latency**          | **0.000061 ms (61ns)**| **0.145 ms (145 us)**| **0.412 ms (412 us)**| **0.720 ms**|
| **P99 Latency**          | **0.000083 ms (83ns)**| **0.310 ms (310 us)**| **0.667 ms (667 us)**| **1.301 ms**|
| **Hardware Platform**    | Apple Silicon M3/ARM| Docker Container   | Localhost loopback| Local loopback|
+--------------------------+---------------------+--------------------+-------------------+---------------+
```

---

## 3. Detailed Boundary Analysis

### 3.1 Scope A: In-Memory Java Dispatch (42 ns P50 / 83 ns P99)
- **Source:** [`research/comparison/PHASE5_1_LATENCY_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/PHASE5_1_LATENCY_AUDIT.md).
- **Measurement Boundary:** Measures strictly the Java CPU instruction cost of evaluating Algorithm 1 against a local in-memory `ClientState` object.
- **Allocation Rate:** **0.00 bytes/op** (zero heap allocations in steady state; uses primitive doubles and in-place field mutations).
- **Auditor Note:** Validates that DREB adds virtually zero CPU overhead to in-process gateway proxies (e.g. Zuul, Spring Cloud Gateway).

### 3.2 Scope B: Redis Lua Execution Engine (0.082 ms P50 / 0.310 ms P99)
- **Source:** [`research/redis/PHASE7_2_END_TO_END_LATENCY.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/redis/PHASE7_2_END_TO_END_LATENCY.md).
- **Measurement Boundary:** Measures the time the Redis primary spends executing the Lua script within its single-threaded event loop, between command dispatch and response buffering.
- **Auditor Note:** Confirms that the mathematical evaluation (five hash field decodes, arithmetic clamps, and hash writes) finishes in **82 microseconds** on the Redis primary.

### 3.3 Scope C: Asynchronous Redis Network RTT (0.259 ms P50 / 0.667 ms P99)
- **Source:** [`research/redis/phase7_2_wait_and_clock_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/redis/phase7_2_wait_and_clock_raw.csv).
- **Measurement Boundary:** Complete application round-trip from the Java gateway: socket write -> TCP loopback -> Redis event loop -> response socket read.
- **Durability Guarantee:** Primary-in-memory only. Subject to un-replicated write loss upon primary crash before asynchronous replication stream drains.
- **Auditor Note:** Sub-millisecond across all percentiles (P99 = 0.667 ms).

### 3.4 Scope D: Synchronous `WAIT 1` Network RTT (0.348 ms P50 / 1.301 ms P99)
- **Source:** [`research/redis/phase7_2_wait_and_clock_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/redis/phase7_2_wait_and_clock_raw.csv).
- **Measurement Boundary:** Application round-trip executing `EVALSHA` followed immediately by `WAIT 1 1000` on the same connection. Gateway blocks until at least 1 read-replica acknowledges the write offset.
- **Durability Guarantee:** Eliminates failover over-admission window caused by un-replicated state loss.
- **Auditor Note:** P99 latency is **$1.301\,\text{ms}$**. This tail latency is dominated by cross-container replication socket handshakes and cannot be claimed as sub-millisecond.

---

## 4. Required Latency Claim Corrections for the Manuscript

1. **Abstract & Section 1:**
   - *Current text:* "...and demonstrate sub-millisecond execution over Redis Cluster."
   - *Corrected text:* **"...and demonstrate sub-millisecond asynchronous execution over Redis Cluster (0.259 ms P50, 0.667 ms P99), with synchronous replication durability (`WAIT 1`) achieving 0.348 ms P50 and 1.301 ms P99 (+95.1% tail latency penalty)."**
2. **Table 5 Caption:**
   - Clearly delineate that Scopes A, B, C, and D reflect different architectural boundaries and trade-offs.
   - Emphasize the PACELC trade-off: trading a +95.1% P99 latency penalty for complete replication durability.
