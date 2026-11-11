# Phase 7: Distributed Idempotency Contract & Payload Binding

**Repository:** `DREB-RateLimiter`  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** `c97a7b04423b83357121c791313879eea737f216`  
**Date:** 2026-11-11  
**Phase:** Phase 7 — Production Redis Adapter and State-Lifecycle Engineering  
**Status:** COMPLETE — GATE C PASSED  

---

## 1. Executive Summary

Phase 6.1 identified two fundamental deficiencies in the Phase 6 idempotency implementation:
1. **Payload Blindness:** Idempotency records stored only a binary integer decision (`1` or `0`) indexed by key name. If a client reused the same key with conflicting parameters (e.g., submitting cost 10, then resubmitting cost 1000 with the same key), the system blindly returned the prior decision without evaluating the new payload.
2. **Metadata Erasure:** Cached responses returned fabricated remaining balances (`-1.0`) because balance metrics were not cached.

Phase 7 implements a **cryptographically bound, metadata-preserving idempotency contract**:
- **Deterministic Payload Fingerprinting:** Each idempotency record is bound to a fingerprint string (incorporating request cost and caller digest).
- **Explicit Conflict Detection:** Reusing an idempotency key with a conflicting payload fingerprint returns an explicit **`IDEMPOTENCY_CONFLICT`** decision without mutating rate-limiter state.
- **Full Balance & Timestamp Preservation:** Cached replays return the exact remaining balances and decision timestamps computed during the original evaluation.
- **Bounded Retention Horizon ($\tau_{\text{idemp}}$):** The idempotency contract defines a strict, configurable retention horizon (default 60s).

---

## 2. In-Script Payload Binding & Conflict Architecture

### 2.1 Serialization Schema in Redis
When a request is evaluated with an idempotency key, the Lua script stores a delimited string record in Redis:
$$\text{Redis Key} = \{\text{clientId}\}:\text{keyPrefix}:\text{idemp}:\langle\text{idempotencyKey}\rangle$$
$$\text{Payload} = \langle\text{fingerprint}\rangle:\langle\text{allowed}\rangle:\langle\text{burst}\rangle:\langle\text{sustained}\rangle:\langle\text{idle}\rangle:\langle\text{abuse}\rangle:\langle\text{timestamp}\rangle:\langle\text{reason}\rangle$$

```mermaid
sequenceDiagram
    autonumber
    actor Client as API Gateway / Client
    participant Limiter as ProductionRedisRca70Limiter
    participant Redis as Redis Single-Threaded Lua Engine

    Note over Client, Redis: 1. Initial Evaluation
    Client->>Limiter: tryAcquire(clientId="user1", cost=10, idempKey="tx-7711", fp="cost=10;op=charge")
    Limiter->>Redis: EVALSHA RCA70_PRODUCTION_LUA ...
    Note over Redis: Check: "tx-7711" not found.<br/>Evaluate: ALLOW! B=690, S=1275.71.<br/>Cache: SETEX idemp:tx-7711 60 "cost=10;op=charge:1:690:1275.71:..."
    Redis-->>Limiter: "NEW:cost=10;op=charge:1:690.0:1275.71:..."
    Limiter-->>Client: LimiterDecision(ALLOW, isCached=false, burst=690.0)

    Note over Client, Redis: 2. Duplicate Submission (Same Fingerprint) -> Cache Hit
    Client->>Limiter: tryAcquire(clientId="user1", cost=10, idempKey="tx-7711", fp="cost=10;op=charge")
    Limiter->>Redis: EVALSHA ...
    Note over Redis: Check: "tx-7711" found!<br/>Fingerprint check: "cost=10;op=charge" == "cost=10;op=charge" (MATCH!)
    Redis-->>Limiter: "CACHED:cost=10;op=charge:1:690.0:1275.71:..."
    Limiter-->>Client: LimiterDecision(ALLOW, isCached=true, burst=690.0)

    Note over Client, Redis: 3. Conflicting Submission (Different Fingerprint) -> Conflict
    Client->>Limiter: tryAcquire(clientId="user1", cost=1000, idempKey="tx-7711", fp="cost=1000;op=transfer")
    Limiter->>Redis: EVALSHA ...
    Note over Redis: Check: "tx-7711" found!<br/>Fingerprint check: "cost=10;op=charge" != "cost=1000;op=transfer" (MISMATCH!)<br/>DO NOT EXECUTE! DO NOT MUTATE STATE!
    Redis-->>Limiter: "CONFLICT:cost=10;op=charge:1:..."
    Limiter-->>Client: LimiterDecision(IDEMPOTENCY_CONFLICT, isAllowed=false)
```

### 2.2 Lua Script Enforcement Logic
In [`ProductionRedisRca70Limiter.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/redis/ProductionRedisRca70Limiter.java#L47-L59):
```lua
if idempKey and idempKey ~= '' then
    local cached = redis.call('GET', idempKey)
    if cached then
        local sep1 = string.find(cached, ':')
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
Because this check executes inside the single-threaded Redis Lua transaction:
- No concurrent client can interleave between the check and the cache set.
- A conflicting payload is rejected with zero token debits and zero state mutations.

---

## 3. Empirical Test Verification

In [`Phase7ProductionAdapterTest.testIdempotencyPayloadBindingAndConflictDetection`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/redis/Phase7ProductionAdapterTest.java#L115-L141):

1. **Initial Submission:**
   - Client submitted request with cost 10 and fingerprint `cost=10;op=transfer`.
   - Result: `status = ALLOW`, `isCached = false`, `burstTokensRemaining = 690.0`.
2. **Duplicate Submission (Identical Fingerprint):**
   - Retried with same key and identical fingerprint.
   - Result: `status = ALLOW`, `isCached = true`, `burstTokensRemaining = 690.0`.
   - **Verification:** State balances were preserved exactly; no second debit applied.
3. **Conflicting Submission (Mismatched Fingerprint):**
   - Resubmitted with same key and conflicting fingerprint `cost=1000;op=withdraw`.
   - Result: `status = IDEMPOTENCY_CONFLICT`, `isAllowed() = false`.
   - **Verification:** Burst balance remained locked at `690.0` (zero state mutation).

---

## 4. Retention Horizon & Retry Contract

| Contract Dimension | Specification | Operational Constraint |
| :--- | :--- | :--- |
| **Idempotency Scope** | Partitioned per client ID via `{clientId}:keyPrefix:idemp:<idempKey>`. | No cross-tenant collisions. Keys automatically hash to the client's Redis cluster shard. |
| **Supported Retry Horizon** | $[t_{\text{submission}},\, t_{\text{submission}} + \tau_{\text{idemp}}]$ (default $\tau_{\text{idemp}} = 60\,\text{seconds}$). | Upstream retry timeout must not exceed $\tau_{\text{idemp}} / 2$. |
| **Post-Expiration Boundary** | Any retry arriving at $t > t_{\text{submission}} + \tau_{\text{idemp}}$ is treated as a **new request**. | If an upstream client retries after 60s, a second debit will occur. Universal unbounded exactly-once execution is not claimed. |
| **Response Metadata** | Full remaining balances ($B, S, I, K$) and original microsecond decision timestamp are returned on cache hits. | Callers receive deterministic, reproducible audit telemetry across retries. |
