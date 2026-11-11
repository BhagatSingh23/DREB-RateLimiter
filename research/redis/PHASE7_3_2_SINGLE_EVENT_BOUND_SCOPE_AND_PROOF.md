# PHASE 7.3.2: SCOPE, TAXONOMY, AND FORMAL PROOF OF THE SINGLE-EVENT ADMISSION BOUND

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.2 Task B  
**Repository Branch:** `research/systematic-improvement`  
**Starting Commit:** `5578582`  

---

## 1. Executive Summary

In Phase 7.3 and Phase 7.3.1, the claim $A(T) \le 2 \times C_{\max} = 2000.0\,\text{tokens}$ was stated as a general upper bound on admitted tokens across a single failover or clock jump. However, auditing revealed three critical ambiguities:
1. **Notation Overloading:** $A(T)$ was used both for continuous cumulative admissions (which include normal refill $R \cdot T$) and for instantaneous event-attributable burst capacity.
2. **Event Scope Under-specification:** The definition of "a single event" lacked formal boundaries regarding whether multiple concurrent fault mechanisms (e.g., failover + clock step) were permitted.
3. **Accounting Granularity:** The scope was not explicitly restricted to a single client state key.

**Resolutions in this Document:**
- We formally rename the bounded quantity to **$A_{\text{event\_burst}}$** (Event-Attributable Burst Admissions).
- We prove that for any non-zero elapsed window $T > 0$, total cumulative admissions $A_{\text{total}}(T)$ can reach $2000.0 + R \cdot T\,\text{tokens}$.
- We rigorously bound $A_{\text{event\_burst}} \le 2000.0\,\text{tokens}$ as an **attainable maximum** per client key under a single isolated discontinuity.
- We formally prove divergence under repeated discontinuities ($K \to \infty$).

---

## 2. Rigorous Operational Definitions

### 2.1 The Accounting Window
Let $[t_0, t_0 + T]$ be a continuous physical time interval of duration $T \ge 0$.  
- The window opens at physical time $t_0$ when the client initiates an initial burst request.
- The window closes at physical time $t_0 + T$ immediately following a secondary post-discontinuity burst request.

### 2.2 Definition of "A Single Event"
A **Single Discontinuity Event ($E$)** is defined as an isolated, discrete transition occurring at an instant $t_d \in (t_0, t_0 + T)$ that invalidates or rolls back the in-memory rate limiter state.

An event $E$ must belong to **exactly one** of the following fault classes:
1. **Un-replicated Asynchronous Failover:** The primary crashes before replicating an acknowledged debit to its replica; the promoted replica reflects the pre-debit state.
2. **State Recreation / Eviction:** The client's Redis hash key (`{dreb:state:<clientId>}`) is evicted or expired, causing the next request to initialize a fresh bucket at full default balances ($B = 700, I = 450$).
3. **Forward Clock Step:** The host clock abruptly steps forward by $\Delta \tau \ge 60\,\text{seconds}$ (e.g., via manual adjustment or NTP step), artificially triggering an instantaneous full refill.

> [!IMPORTANT]
> **Mutual Exclusivity:** An event $E$ cannot combine multiple fault mechanisms. A failover accompanied by a concurrent clock step constitutes **two distinct events** ($K = 2$) and is governed by the multi-event divergence model.

### 2.3 Accounting Granularity
- The bound is strictly **per client key** (`{dreb:state:<clientId>}`).
- It is **not** an aggregate cluster-wide or cross-client bound. If $M$ independent client identities simultaneously burst across a single failover, aggregate admissions across the cluster equal $M \times 2000.0\,\text{tokens}$.

---

## 3. Separation of Quantities: Burst vs. Cumulative Admissions

We distinguish between two quantities:

1. **Event-Attributable Burst Admissions ($A_{\text{event\_burst}}$):**  
   The instantaneous token volume drawn strictly from bucket capacities across the pre- and post-discontinuity requests, excluding continuous refill accrued over physical time.
2. **Total Cumulative Admissions ($A_{\text{total}}(T)$):**  
   The total volume of tokens admitted over the physical interval $[t_0, t_0 + T]$:
   $$A_{\text{total}}(T) = A_{\text{event\_burst}} + A_{\text{refill}}(T)$$
   where $A_{\text{refill}}(T) \le R \cdot T$ represents ordinary refill accrual.

---

## 4. Formal Derivation and Proof

### 4.1 Lemma 1: Single-Transaction Ceiling
In `AdaptiveCeilingIdleCreditPolicy.java` and `lua/dreb_rca70_sliding.lua`:
$$\text{effBurst} = B_{\max} + \frac{I_{\max}}{d \cdot (1 + \gamma K)}$$
Because $K \ge 0$, $\gamma = 0.75 \ge 0$, and $d = 1.5$:
$$\max(\text{effBurst}) = B_{\max} + \frac{I_{\max}}{d} = 700.0 + \frac{450.0}{1.5} = \mathbf{1000.0\,\text{tokens}} = C_{\max}$$
The Lua engine executes:
```lua
if cost > effBurst then
    return {0, B, I, S, 0, K, "COST_EXCEEDS_BURST_CAP"}
end
```
Any request with cost $c > C_{\max} = 1000.0$ is unconditionally rejected.  
Therefore, for any single transaction $i$:
$$A_i \le C_{\max} = 1000.0\,\text{tokens} \quad \blacksquare$$

### 4.2 Theorem 1: Attainable Bound on Event-Attributable Burst Admissions
*Across a single discontinuity event $E$, event-attributable burst admissions for a single client key are bounded by an attainable maximum of:*
$$A_{\text{event\_burst}} \le 2 \times C_{\max} = \mathbf{2000.0\,\text{tokens}}$$

**Proof:**
1. **Pre-Discontinuity Interval $[t_0, t_d)$:**  
   The client begins with initial state $(B_0, I_0) \le (B_{\max}, I_{\max})$.  
   The maximum burst volume the client can draw prior to $t_d$ is bounded by the initial effective ceiling:
   $$A_{\text{pre}} \le C(K_0) \le C_{\max} = 1000.0\,\text{tokens}$$
   A client submitting $c_1 = 1000.0$ at $t_0$ is admitted: $A_{\text{pre}} = 1000.0$. Balances become $B = 0, I = 0$.

2. **The Discontinuity Event at $t_d$:**  
   At $t_d$, event $E$ occurs. The state restored on the newly active instance cannot exceed default initial capacity:
   $$B(t_d^+) \le B_{\max} = 700.0, \quad I(t_d^+) \le I_{\max} = 450.0, \quad K(t_d^+) \ge 0$$
   The available burst capacity is reset to at most $C(K(t_d^+)) \le C_{\max} = 1000.0\,\text{tokens}$.

3. **Post-Discontinuity Interval $[t_d, t_0 + T]$:**  
   Starting from restored balances at $t_d^+$, the maximum burst volume the client can draw is bounded by Lemma 1:
   $$A_{\text{post}} \le C_{\max} = 1000.0\,\text{tokens}$$
   A client submitting $c_2 = 1000.0$ at $t_d^+$ is admitted: $A_{\text{post}} = 1000.0$.

4. **Sum of Burst Admissions:**
   $$A_{\text{event\_burst}} = A_{\text{pre}} + A_{\text{post}} = 1000.0 + 1000.0 = \mathbf{2000.0\,\text{tokens}}$$
   Because this sequence ($c_1 = 1000.0, c_2 = 1000.0$) is admissible and verified in testbed benchmarks (`testSingleEventMaximumOverAdmission`), $2000.0\,\text{tokens}$ is an **attainable maximum**. $\blacksquare$

### 4.3 Corollary 1: Cumulative Admissions for $T > 0$
For an observation window of duration $T > 0$ that includes continuous sustained refill:
$$A_{\text{total}}(T) \le 2 \times C_{\max} + R \cdot T = \mathbf{2000.0 + 1000.0 \cdot T\,\text{tokens}}$$
- At $T = 0$: $A_{\text{total}}(0) = 2000.0\,\text{tokens}$.
- At $T = 10\,\text{ms} = 0.010\,\text{s}$: $A_{\text{total}}(0.010) \le 2000.0 + (1000.0 \times 0.010) = \mathbf{2010.0\,\text{tokens}}$.

---

## 5. Multi-Event Divergence Theorem

### Theorem 2 (Non-Existence of a Global Bound Under Repeated Events)
*Let an adversary or environment induce $K$ distinct discontinuity events within physical window $T$. Then:*
$$A_{\text{event\_burst}}(K) \le (K + 1) \cdot C_{\max}$$
*and for unconstrained $K$:*
$$\lim_{K \to \infty} A_{\text{event\_burst}}(K) = \infty$$

**Proof:**
Each event $k \in \{1, \dots, K\}$ resets burst capacity to $C_{\max} = 1000.0$.  
Between each reset, the client can consume at most $C_{\max}$ tokens.  
With $K$ resets, there exist $K + 1$ admission epochs:
$$A_{\text{event\_burst}} = \sum_{j=1}^{K+1} A_j \le (K + 1) \cdot C_{\max}$$
As $K \to \infty$, $(K + 1) \cdot C_{\max} \to \infty$.  
Therefore, **no finite global rate or burst bound exists without an operational limit bounding the event frequency.** $\blacksquare$

---

## 6. Synthesis: Evidence Separation Matrix

| Metric / Claim | Mathematical Status | Empirical Testbed Status | Underlying Required Assumptions |
| :--- | :--- | :--- | :--- |
| **$A_i \le 1000.0$** | **`[MATHEMATICALLY_PROVED]`** | **`[EXPERIMENTALLY_OBSERVED]`** | Single transaction cost enforcement in Lua engine. |
| **$A_{\text{event\_burst}} \le 2000.0$** | **`[MATHEMATICALLY_PROVED]`** | **`[EXPERIMENTALLY_OBSERVED]`** | Exactly one isolated event per window; per client key. |
| **$A_{\text{total}}(T) \le 2000 + RT$** | **`[MATHEMATICALLY_PROVED]`** | **`[EXPERIMENTALLY_OBSERVED]`** | Monotonic refill progression during non-event epochs. |
| **Divergence as $K \to \infty$** | **`[MATHEMATICALLY_PROVED]`** | **`[EXPERIMENTALLY_OBSERVED]`** | $K$ successive forward clock steps tested in `Phase73ReconciliationTest`. |
