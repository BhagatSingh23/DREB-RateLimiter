# PHASE 7.3.3: VERIFICATION, ATTAINABILITY, AND ACCOUNTING OF THE SINGLE-EVENT BOUND

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.3 Task C  
**Repository Branch:** `research/systematic-improvement`  
**Starting Commit:** `2016eeb`  

---

## 1. Executive Summary

Phase 7.3.3 conducts an exhaustive code-level audit of the transition equations in `AdaptiveCeilingIdleCreditPolicy.java` and `lua/dreb_rca70_sliding.lua` to verify the single-event admission bound:
$$A_{\text{event\_burst}} \le 2 \times C_{\max} = 2000.0\,\text{tokens}$$

**Audit Verdict:**
1. **Attainability Confirmed:** $2000.0\,\text{tokens}$ is **not** merely an unreachable theoretical supremum; it is an **attainable maximum** achievable via a valid two-request sequence ($c_1 = 1000.0, c_2 = 1000.0$) across an un-replicated failover or forward clock step.
2. **Cumulative Total Formula Verified:** The relationship $A_{\text{total}}(T) \le 2000.0 + R \cdot T$ holds strictly without double-counting or omitted admissions.
3. **Per-Key Scope Re-verified:** The bound applies strictly per client state key (`{dreb:state:<clientId>}`). It does not bound aggregate load across distinct clients.

---

## 2. Transition Equation Inspection

In both the Java reference implementation and the Redis Lua script, burst capacity is governed by three equations:

### 2.1 Effective Burst Capacity Function
$$\text{effBurst}(K) = B + \frac{I}{d \cdot (1 + \gamma K)}$$
With parameters:
- $B_{\max} = 700.0\,\text{tokens}$
- $I_{\max} = 450.0\,\text{tokens}$
- $d = 1.5$ (Idle debit penalty)
- $\gamma = 0.75$ (Ceiling suppression factor)
- $K \ge 0$ (Abuse score)

Because $K \ge 0$, the maximum possible value of $\text{effBurst}$ occurs when the client is fully rested ($K = 0$) with full balances ($B = B_{\max}, I = I_{\max}$):
$$C_{\max} = \max(\text{effBurst}) = 700.0 + \frac{450.0}{1.5 \times (1 + 0)} = 700.0 + 300.0 = \mathbf{1000.0\,\text{tokens}}$$

### 2.2 Admission Guard
```lua
if cost > effBurst or cost > S then
    return {0, B, I, S, 0, K, "COST_EXCEEDS_BURST_CAP"}
end
```
Because $S_{\max} = 1285.7143 > 1000.0$, the effective burst ceiling $C_{\max} = 1000.0$ is the binding constraint.  
**Consequence:** No individual transaction cost $c > 1000.0$ can be admitted by the limiter:
$$\forall i, \quad A_i \le C_{\max} = 1000.0\,\text{tokens}$$

---

## 3. Proof of Attainability for $A_{\text{event\_burst}} = 2000.0\,\text{tokens}$

We demonstrate that $A_{\text{event\_burst}} = 2000.0\,\text{tokens}$ is an attainable maximum by tracing the exact execution of a valid two-request sequence across an isolated discontinuity event $E$:

```mermaid
sequenceDiagram
    autonumber
    Note over Client,Primary: Initial State: B=700, I=450, S=1285.71, K=0
    Client->>Primary: Request 1: cost c1 = 1000.0 (t = t0)
    Note over Primary: effBurst = 1000.0 >= 1000.0 -> ALLOWED (A1 = 1000.0)
    Note over Primary: Post-debit: B=0, I=0, S=285.71, K=0.6667
    Note over Primary: Write committed to Primary RAM; Primary crashes before replication!
    Note over Replica: Discontinuity Event E: Replica promoted holding stale state (B=700, I=450)
    Client->>Replica: Request 2: cost c2 = 1000.0 (t = td+)
    Note over Replica: effBurst = 1000.0 >= 1000.0 -> ALLOWED (A2 = 1000.0)
    Note over Replica: Post-debit: B=0, I=0, S=285.71, K=0.6667
    Note over Client: Total Burst Admissions = A1 + A2 = 2000.0 tokens!
```

### 3.1 Trace Analysis
1. **Pre-Event Request ($t = t_0$):**
   - Cost: $c_1 = 1000.0$.
   - Guard check: $\text{effBurst} = 1000.0 \ge 1000.0$ $\implies$ **ALLOWED**.
   - Admitted: $A_1 = 1000.0\,\text{tokens}$.
   - Balances depleted: $B = 0.0, I = 0.0$.
2. **Discontinuity Event ($t = t_d$):**
   - In an un-replicated failover under asynchronous replication, the promoted replica contains the initial state: $B = 700.0, I = 450.0, K = 0.0$.
   - Capacity at $t_d^+$ is restored to $C_{\max} = 1000.0\,\text{tokens}$.
3. **Post-Event Request ($t = t_d^+$):**
   - Cost: $c_2 = 1000.0$.
   - Guard check: $\text{effBurst} = 1000.0 \ge 1000.0$ $\implies$ **ALLOWED**.
   - Admitted: $A_2 = 1000.0\,\text{tokens}$.
4. **Sum of Burst Admissions:**
   $$A_{\text{event\_burst}} = A_1 + A_2 = 1000.0 + 1000.0 = \mathbf{2000.0\,\text{tokens}}$$

Because all steps strictly conform to the production Lua script and were verified in `Phase73ReconciliationTest.testSingleEventMaximumOverAdmission`, **the value 2000.0 tokens is an attainable maximum**.

---

## 4. Total Cumulative Admissions Over Elapsed Interval $T$

Let $[t_0, t_0 + T]$ be an observation window containing exactly one isolated event $E$ at $t_d$.

1. **Burst Contribution:** At most two capacity-drawing bursts can occur (one before $t_d$ and one after $t_d$):
   $$A_{\text{event\_burst}} \le 2 \times C_{\max} = 2000.0\,\text{tokens}$$
2. **Continuous Accrual Contribution:** Over physical interval $T$, continuous sustained refill accrues at rate $R = 1000.0\,\text{tokens/s}$:
   $$A_{\text{refill}}(T) = \int_{t_0}^{t_0 + T} R_{\text{accrual}}(t)\,dt \le R \cdot T$$
3. **Total Cumulative Upper Bound:**
   $$A_{\text{total}}(T) = A_{\text{event\_burst}} + A_{\text{refill}}(T) \le \mathbf{2000.0 + 1000.0 \cdot T\,\text{tokens}}$$

### 4.1 Absence of Double-Counting
- Burst requests $c_1$ and $c_2$ draw from available bucket balances.
- Once bucket balances are exhausted ($B = 0, I = 0$), any subsequent request within $[t_0, t_0 + T]$ can only be admitted if tokens have accrued via continuous refill $R \cdot T$.
- Hence, $A_{\text{total}}(T) \le 2000.0 + R \cdot T$ partitions admissions into independent orthogonal components: capacity resets and time-based refill. No admissions are omitted, and no capacity is double-counted.

---

## 5. Formal Scope Summary

| Dimension | Exact Scope | Attainability |
| :--- | :--- | :---: |
| **Event-Attributable Burst ($A_{\text{event\_burst}}$)** | $\le 2000.0\,\text{tokens}$ per client key | **Attainable Maximum** |
| **Cumulative Admissions ($A_{\text{total}}(T)$)** | $\le 2000.0 + 1000.0 \cdot T\,\text{tokens}$ per client key | **Attainable Bound** |
| **Accounting Scope** | Per client state hash (`{dreb:state:<clientId>}`) | Per-key |
| **Multi-Client Aggregation** | $M$ independent client keys bursting simultaneously admit $M \times 2000.0\,\text{tokens}$ | Linear in $M$ |
| **Repeated Discontinuities** | For $N_E(T)$ discrete events: $A \le (N_E(T) + 1) \cdot C_{\max} \to \infty$ | Divergent |
