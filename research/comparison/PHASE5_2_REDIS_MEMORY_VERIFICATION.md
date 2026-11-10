# Phase 5.2 — Redis Memory Footprint Empirical Verification

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`4ed10a8`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Class:** [`RedisStateStore.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/RedisStateStore.java)  
**Redis Server Version:** Redis 7.4.11 (64-bit)  
**Memory Allocator:** jemalloc-5.3.0  
**Host Environment:** Linux 6.12.76-linuxkit aarch64 (Docker container in arm64 Darwin VM)

---

## 1. Executive Summary

In Phase 5.1, the memory overhead of storing DREB rate-limiter state in a distributed Redis deployment was estimated at $\approx 200 - 250\,\text{bytes}$ per client key.

This investigation conducted direct empirical measurements against an active, standalone **Redis 7.4.11** instance to verify the precise per-key memory consumption across multiple key cardinalities ($N=1,000$, $N=10,000$, $N=100,000$ keys).

> **Empirical Verification Finding:**
> 1. Single-key inspection via `MEMORY USAGE` reports exactly **$192\,\text{bytes}$** for standard client identifiers (`dreb:client_0000001`).
> 2. Total incremental process memory (`used_memory`) measured across cardinalities yields:
>    - **$N = 1,000$ keys:** $185.41\,\text{bytes / key}$ ($185.4\,\text{KB}$ total).
>    - **$N = 10,000$ keys:** $205.11\,\text{bytes / key}$ ($2.05\,\text{MB}$ total).
>    - **$N = 100,000$ keys:** $202.49\,\text{bytes / key}$ ($20.25\,\text{MB}$ total).
> 3. The historical estimate of $\approx 200 - 250\,\text{bytes}$ is **confirmed as empirically accurate** for Redis 7 instances utilizing the default `listpack` hash encoding.

---

## 2. Benchmark Configuration and State Serialization

The measurements replicate the exact state schema used by [`RedisStateStore.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/RedisStateStore.java):

### 2.1 Key and Value Representation
- **Key Schema:** `dreb:client_<7-digit-id>` (19 ASCII bytes, e.g. `"dreb:client_0000042"`).
- **Data Structure:** Redis `HASH`.
- **Field Count:** Exactly 5 fields per client key.
  1. `burstTokens`: `"700.0"`
  2. `sustainedTokens`: `"1285.7142857"`
  3. `idleCredit`: `"0.0"`
  4. `abuseScore`: `"0.0"`
  5. `lastRefillTs`: `"1700000000.123"`
- **Internal Encoding:** `listpack` (confirmed via `OBJECT ENCODING`).

---

## 3. Empirical Measurement Results

Measurements were collected on isolated database index 15. The database was flushed before each cardinality run, and baseline `used_memory` was captured prior to key generation.

| Key Cardinality ($N$) | Initial `used_memory` (Bytes) | Final `used_memory` (Bytes) | Delta `used_memory` (Bytes) | Incremental Memory per Key | Per-Key `MEMORY USAGE` (Sample) | Internal Encoding |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **$1,000$ keys** | $1,360,840$ | $1,546,248$ | $185,408$ | **$185.41\,\text{bytes / key}$** | $192\,\text{bytes}$ | `listpack` |
| **$10,000$ keys** | $1,360,840$ | $3,411,976$ | $2,051,136$ | **$205.11\,\text{bytes / key}$** | $192\,\text{bytes}$ | `listpack` |
| **$100,000$ keys** | $1,360,840$ | $21,609,480$ | $20,248,640$ | **$202.49\,\text{bytes / key}$** | $192\,\text{bytes}$ | `listpack` |

### 3.1 Breakdown of Redis Overhead
For each client hash, Redis allocates:
1. **Dictionary Entry (`dictEntry`):** 24 bytes in the main keyspace dictionary.
2. **Redis Object (`robj`):** 16 bytes representing the hash metadata pointer.
3. **Key SDS String:** 19 bytes payload + 3 bytes header/null = 24 bytes (jemalloc bin).
4. **Listpack Payload:** 5 field names + 5 string values encoded into a contiguous byte buffer $\approx 85 - 95\,\text{bytes}$.
5. **Jemalloc Arena Allocation Bins:** jemalloc rounds small allocations to power-of-two size classes (e.g. 192 bytes, 224 bytes, 256 bytes).

---

## 4. Operational Boundaries & Scope Qualifications

To prevent over-generalization in production planning, the following qualifications are documented:

1. **Hash Encoding Thresholds:**
   Redis 7 stores hashes as compact `listpack` buffers as long as the entry count is $\le 128$ (`hash-max-listpack-entries`) and all string values are $\le 64\,\text{bytes}$ (`hash-max-listpack-value`). Because DREB uses exactly 5 small numeric fields, **100% of DREB limiters remain in listpack format**.
   If a custom implementation stores large metadata causing conversion to `hashtable`, memory consumption increases to $\approx 400 - 450\,\text{bytes}$ per client.
2. **TTL Eviction Overhead:**
   If keys are configured with expiry (`EXPIRE`), Redis maintains a secondary expiry dictionary (`db->expires`), adding an additional $\approx 32\,\text{bytes}$ per expiring key.
3. **Persistence and Replication Buffers:**
   These measurements reflect dataset memory only. In production environments with AOF rewriting, RDB snapshotting (`BGSAVE`), or high-throughput replica replication, copy-on-write memory pages and replication backlog buffers (`repl-backlog-size`) introduce system-level overhead beyond per-key allocations.
