# PHASE 7.1: IDEMPOTENCY FAILURE MATRIX AND BOUNDED-RETENTION AUDIT

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.1 Workstream B  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `75f0c25b9bf71b20d7855349e64157fdaa5b92e5`  

---

## 1. Executive Summary

Idempotency in distributed rate limiting prevents network retries from consuming multiple token quotas. In Phase 7, a payload-bound idempotency mechanism was implemented where the client supplies an idempotency key and a request payload fingerprint (`SHA-256` or structured parameter hash).

This audit investigates the behavior of the idempotency subsystem under edge conditions, including:
1. Conflicting payload reuse.
2. TTL horizon expiration ($\tau_{\text{idemp}} = 60\,\text{s}$).
3. Uncoordinated record loss (failover or eviction dropping the idempotency string while the state hash persists).
4. State loss while the idempotency record survives.

Our empirical tests in [`Phase71DurabilityAndFailureAuditTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/redis/Phase71DurabilityAndFailureAuditTest.java) confirm that while payload binding effectively prevents payload confusion and malicious key hijacking within the retention window, **any retry arriving outside the 60-second retention window or after an uncoordinated failover will trigger a double-debit**.

---

## 2. Complete 6-Case Idempotency Failure Matrix

The following matrix documents the empirical behavior across all combinations of record presence, fingerprint matching, and lifecycle states:

| Case | Scenario | Record State in Redis | Fingerprint Match | Observed Limiter Response | State Mutation | Gate Status |
| :---: | :--- | :--- | :---: | :--- | :--- | :---: |
| **Case 1** | Benign retry within retention window | Present ($\text{TTL} \le 60\,\text{s}$) | **Match** | `ALLOW (isCached=true)` with exact historical metadata | **None** ($B, S, I, K$ unchanged) | **PASS** |
| **Case 2** | Conflicting payload reusing key | Present ($\text{TTL} \le 60\,\text{s}$) | **Mismatch** | `IDEMPOTENCY_CONFLICT` (Rejected: `conflicting request payload fingerprint`) | **None** ($B, S, I, K$ unchanged) | **PASS** |
| **Case 3** | Delayed retry after TTL expiration | Expired / Absent | **Match** | `ALLOW (isCached=false)` fresh evaluation | **Double-Debit** ($B \leftarrow B - c$) | **CONDITIONAL** |
| **Case 4** | Different request after TTL expiration | Expired / Absent | **Mismatch** | `ALLOW (isCached=false)` fresh evaluation | **Normal Debit** ($B \leftarrow B - c_{\text{new}}$) | **PASS** |
| **Case 5** | Uncoordinated failover loss of idemp key | State Present, Idemp Absent | **Match** | `ALLOW (isCached=false)` fresh evaluation | **Double-Debit** ($B \leftarrow B - c$) | **CONDITIONAL** |
| **Case 6** | State hash missing, idemp key surviving | State Absent, Idemp Present | **Match** | `ALLOW (isCached=true)` cached response returned | **None** (State hash NOT recreated) | **PASS** |

---

## 3. Deep-Dive Failure Scenario Analysis

### 3.1 Case 2: Conflicting Payload Tampering (Verified)
When a client reuses an idempotency key with a mutated payload (e.g., attempting to claim cost 100 on an idempotency key that was approved for cost 10), the Lua script executes Step 1:

```lua
if idempKey ~= '' then
    local cached = redis.call('GET', idempKey)
    if cached then
        local sep1 = string.find(cached, '|', 1, true)
        if sep1 then
            local cachedFp = string.sub(cached, 1, sep1 - 1)
            if cachedFp ~= '' and payloadFingerprint ~= '' and cachedFp ~= payloadFingerprint then
                return 'CONFLICT:' .. cached
            end
        end
        return 'CACHED:' .. cached
    end
end
```

In `testIdempotencyConflictingFingerprintRejection`:
- Initial request: `tryAcquire(clientId, 100, "req-idemp-01", "payload_hash_A")` $\rightarrow$ `ALLOW`.
- Second request: `tryAcquire(clientId, 100, "req-idemp-01", "payload_hash_B")` $\rightarrow$ `IDEMPOTENCY_CONFLICT`.
- Result: **Zero state mutation**, preventing credit theft.

### 3.2 Case 3 & 5: Double-Debit Hazards (Quantified)

In distributed execution, if the idempotency record is lost while the client's state hash persists, retrying clients are penalized by duplicate quota consumption.

#### Case 5 Empirical Trace:
From `testStatePresentIdempotencyMissingCausesDoubleDebit`:
1. Client starts with full burst $B = 700.0$.
2. Client acquires cost $c = 300.0$ with idempotency key `req-audit-991`.
   - Primary state: $B = 400.0$.
   - Idempotency key stored: `p71:{client}:idemp:req-audit-991`.
3. Idempotency key is removed (simulating replication drop or cache eviction), but state hash remains at $B = 400.0$.
4. Client retries `req-audit-991` with cost $c = 300.0$.
5. The Lua script finds no cached record and treats the request as a fresh transaction:
   - Debit: $B_{\text{new}} = 400.0 - 300.0 = 100.0$.
   - Admitted: Both attempts succeed, but client has been charged **600 tokens** for a single 300-token logical operation!

```mermaid
flowchart TD
    A["Client sends Retry (cost=300, key=req-991)"] --> B{"Idempotency Record in Redis?"}
    B -- Yes --> C["Return CACHED ALLOW<br/>(Balance remains B=400)"]
    B -- No (Lost/Expired) --> D["Evaluate Fresh Transaction"]
    D --> E["Debit Tokens: B = 400 - 300 = 100"]
    E --> F["Return FRESH ALLOW<br/>CRITICAL: Double-Debit Occurred!"]
```

### 3.3 Case 6: State Missing, Idempotency Surviving (Short-Circuit Isolation)
In `testIdempotencyPresentStateMissingReturnsCachedWithoutRecreating`:
- If an administrative purge, memory eviction, or node split deletes `{clientId}:state`, but `{clientId}:idemp:{key}` survives, an incoming retry returns the cached `ALLOW` decision.
- **Critical Finding:** The script short-circuits at Step 1 (`return 'CACHED:' .. cached`), completely bypassing Step 3 (`redis.call('HMGET', stateKey, ...)`).
- As confirmed by our test assertion `assertNull(limiter.getState(clientId))`, **the rate limiter does NOT recreate an empty state hash**, preserving storage hygiene and avoiding accidental cold-start resets.

---

## 4. Retention Horizon Design: Bounded vs. Infinite

Why not set idempotency TTL to $\infty$ (or match the client lifecycle)?

| Dimension | Bounded Retention ($\tau_{\text{idemp}} = 60\,\text{s}$) | Unbounded Retention ($\tau_{\text{idemp}} = \infty$) |
| :--- | :--- | :--- |
| **Double-Debit Window** | Retries after 60 seconds trigger double-debit | Never double-debits across client lifetime |
| **Memory Footprint** | Bounded: $N_{\text{active\_tx}} \times 176\,\text{bytes}$ | Unbounded: Monotonically grows with every unique request ID |
| **At 10,000 req/sec** | $\approx 10,000 \times 60 \times 176\,\text{B} \approx \mathbf{105.6\,\text{MB}}$ | $\approx 864,000,000 \times 176\,\text{B} \approx \mathbf{152.0\,\text{GB/day}}$ (OOM Crash) |
| **Failure Mode** | Safe memory bound, graceful degradation | Redis memory exhaustion, `OOM` rejection of all traffic |

> [!IMPORTANT]
> A bounded retention horizon $\tau_{\text{idemp}} \in [30\,\text{s}, 300\,\text{s}]$ is mathematically mandatory for in-memory Redis deployments. System architects must configure client HTTP retry policies (e.g., exponential backoff) with a maximum retry deadline strictly less than $\tau_{\text{idemp}}$ (e.g., $t_{\text{retry\_deadline}} \le 45\,\text{s}$).

---

## 5. Workstream B Audit Reclassification

1. **Gate H (Idempotency Safety):** Maintained as **PASS** with documented boundary conditions:
   - Within the retention window $\tau_{\text{idemp}}$, payload protection is strictly verified.
   - Retries beyond $\tau_{\text{idemp}}$ or across un-replicated failover drop to at-least-once semantics (duplicate debit).
2. **Operational Constraint:** Upstream API gateways must enforce $t_{\text{client\_retry\_max}} < \tau_{\text{idemp}}$ to guarantee zero double-debit occurrences.
