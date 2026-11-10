# Phase 5.4.1 — Request Semantics Audit: Atomic vs. Token-Level Rejection

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`62a3774`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Artifacts:** [`RateLimiter.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/RateLimiter.java), [`Decision.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/Decision.java), [`DREBLimiter.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/DREBLimiter.java), [`TokenBucketLimiter.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/TokenBucketLimiter.java)  
**Verification Test Suite:** [`Phase541RequestSemanticsTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase541RequestSemanticsTest.java)

---

## 1. Executive Summary & Audit Objective

The Phase 5.4 audit established that Static Dual-Bucket fails operational eligibility under nominal bursts because of its rejection rate ($30.0\% > 5.0\%$). However, the independent review correctly noted that Phase 5.4 treated token-level partial rejection ($700$ admitted, $300$ rejected) and request-level rejection as interchangeable.

This audit investigates the **actual limiter API contract** and the **admission decision path** in code to establish:
1. Whether nominal-capacity burst requests are atomic or splittable.
2. The exact request-level and token-level false rejection rates under production API semantics.
3. Whether partial admission is supported by the production-facing interface.

---

## 2. API Contract & Decision Path Inspection

An inspection of the production package `com.dreb.ratelimit` reveals the following structural invariants:

### 2.1 The Public Contract (`RateLimiter.java`)
```java
public interface RateLimiter {
    default Decision allow(String clientId) {
        return allow(clientId, 1);
    }
    Decision allow(String clientId, int cost);
}
```
The interface exposes a binary return type: `Decision allow(String clientId, int cost)`.

### 2.2 The Decision Enum (`Decision.java`)
```java
public enum Decision {
    ALLOW,
    DENY
}
```
There are only two possible outcomes: `ALLOW` or `DENY`. The interface does **not** expose a partial admission type (e.g. `PartialDecision`, `int tokensAllowed`).

### 2.3 The Core Decision Logic (`DREBLimiter.java`)
```java
protected Decision checkAndConsume(ClientState state, int cost) {
    double effectiveBurst = state.burstTokens + idlePolicy.effectiveCredit(state);
    if (state.sustainedTokens >= cost && effectiveBurst >= cost) {
        state.sustainedTokens -= cost;
        double deficit = Math.max(0.0, cost - state.burstTokens);
        state.burstTokens = Math.max(0.0, state.burstTokens - cost);
        if (deficit > 0.0) {
            idlePolicy.spend(state, deficit);
        }
        return Decision.ALLOW;
    }
    return Decision.DENY;
}
```
In `checkAndConsume`:
- If `effectiveBurst < cost`, the limiter updates nothing and immediately returns `Decision.DENY`.
- There is no fallback mechanism to deduct partial tokens. The request is **strictly atomic**.

---

## 3. Evaluation of Nominal Burst Request ($C = 1000$)

Consider a client with nominal contracted sustained rate $R = 1000\,\text{tokens/s}$ and nominal burst capacity $C = 1000\,\text{tokens}$. The client rests for $10\,\text{seconds}$ to fully replenish all buckets and credits, and then issues a nominal-capacity burst request of `cost=1000`.

### 3.1 Policy-by-Policy Evaluation Matrix

| Metric / Attribute | Static Dual-Bucket (`static_db_070c`) | DREB-RCA70 (`rca70`) | Token Bucket (`tb_large`) |
| :--- | :---: | :---: | :---: |
| **Bucket Parameters** | $B_{\max} = 700, I_{\max} = 0$ | $B_{\max} = 700, I_{\max} = 450$ | $C = 1000, R = 1000$ |
| **Available Effective Burst** | $700.0\,\text{tokens}$ | $700 + 450/1.5 = 1000.0\,\text{tokens}$ | $1000.0\,\text{tokens}$ |
| **Decision Returned** | `Decision.DENY` | `Decision.ALLOW` | `Decision.ALLOW` |
| **Requests Offered** | $1$ | $1$ | $1$ |
| **Requests Admitted** | $0$ | $1$ | $1$ |
| **Requests Rejected** | $1$ | $0$ | $0$ |
| **Request-Level FRR** | **$100.0\%$** | **$0.0\%$** | **$0.0\%$** |
| **Tokens Offered** | $1000$ | $1000$ | $1000$ |
| **Tokens Admitted** | $0$ | $1000$ | $1000$ |
| **Tokens Rejected** | $1000$ | $0$ | $0$ |
| **Token-Level Rejection Fraction** | **$100.0\%$** | **$0.0\%$** | **$0.0\%$** |
| **Partial Admission Supported?** | **NO** | **N/A (Fully Admitted)** | **N/A (Fully Admitted)** |

### 3.2 Key Scientific Findings
1. **Production Request Semantics are Atomic:**
   Under the production `RateLimiter` interface, a single request of `cost=1000` sent to Static Dual-Bucket results in **complete rejection**:
   $$\text{FRR}_{\text{request}} = \mathbf{100.0\%}, \quad \text{Rejection}_{\text{token}} = \mathbf{100.0\%}$$
2. **Partial Admission is a Hypothetical Construct:**
   The reported $30.0\%$ rejection fraction / $70.0\%$ burst acceptance rate in Phase 5.3/5.4 assumed a hypothetical streaming decomposition where the burst was divided into $1000$ individual $1\text{-token}$ requests. 
3. **SLA Disqualification Holds Under Both Semantics:**
   Whether evaluated as:
   - **Atomic request:** $\text{FRR}_{\text{burst}} = 100.0\% \gg 5.0\%$, or
   - **Streaming tokens:** $\text{FRR}_{\text{burst}} = 30.0\% \gg 5.0\%$,
   Static Dual-Bucket violates the declared $5.0\%$ false rejection rate SLA by at least $6\times$.
4. **Mandatory Standard for Production SLAs:**
   In real-world API gateways (e.g. Envoy, Kong, Spring Cloud Gateway), rate limiters return HTTP 200 or HTTP 429. If a batch request of weight $1000$ is rejected, the gateway returns HTTP 429; it does not partially execute the batch. Therefore, **request-level FRR ($100.0\%$) is the definitive metric** for operational SLA compliance.
