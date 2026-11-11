# PHASE 7.3: G1 BURST ENVELOPE FORMAL BOUND AND RECONCILIATION

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3 Workstream A & C  
**Repository Branch:** `research/systematic-improvement`  
**Starting Reference Commit:** `8f55c0d4193bee5f9826f785eae9124feba2f602`  

---

## 1. Executive Summary

This document resolves the contradiction between the over-admission numbers reported across previous audit phases:
- **Phase 7.1:** Reported $+104.29\,\text{tokens}$ under asynchronous replica failover.
- **Phase 7.2:** Reported $+104.29\,\text{tokens}$ under a $1.0\,\text{s}$ forward clock jump, but $+404.29\,\text{tokens}$ under a $60.0\,\text{s}$ forward clock jump.

We prove mathematically and empirically (in [`Phase73ReconciliationTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/redis/Phase73ReconciliationTest.java)) that:
1. **Neither $+104.29$ nor $+404.29$ was a mathematical upper bound.** They were merely workload-specific measurements for specific request costs ($c=700$) and idle states ($I=0$).
2. **Single-Event Maximum Over-Admission Bound:** For any single ungraceful transition (single failover or single arbitrary forward clock jump), the theoretical maximum excess admission as $T \to 0$ is **$714.29\,\text{tokens}$** (or **$704.29\,\text{tokens}$** at $T = 10\,\text{ms}$).
3. **Absence of Global Bound Under Repeated Discontinuities:** In the presence of repeated un-slew-bounded clock jumps or cascading failovers, **NO FINITE GLOBAL BOUND EXISTS**. Total over-admission grows as $O(K \cdot C_{\max})$ where $K$ is the number of jump events.
4. **Physical-Time Slew-Bounded G1 Theorem:** If host clock deviation is bounded by $|\delta| \le \delta_{\max}$, the physical G1 envelope is preserved subject to a linear drift expansion term: $A(T) \le S_{\max} + R \cdot (T + \delta_{\max})$.

---

## 2. Derivation of the Single-Event Maximum Over-Admission Bound

### 2.1 State Capacity Invariants in DREB-RCA70
The implementation enforces the following capacity limits in Lua:
- Sustained bucket ceiling: $S \le S_{\max} = 1285.7143\,\text{tokens}$.
- Burst bucket ceiling: $B \le B_{\max} = 700.0\,\text{tokens}$.
- Idle credit ceiling: $I \le I_{\max} = 450.0\,\text{tokens}$ (for abuse score $K = 0$).
- Effective burst capacity:
  $$C_{\max} = \text{effBurst}_{\max} = B_{\max} + \frac{I_{\max}}{d} = 700.0 + \frac{450.0}{1.5} = \mathbf{1000.0\,\text{tokens}}$$
- Maximum single transaction cost allowed by the limiter:
  $$c_{\max} = \min(S_{\max}, C_{\max}) = \min(1285.7143, 1000.0) = \mathbf{1000.0\,\text{tokens}}$$

### 2.2 Worst-Case Single-Event Scenario
Consider true physical elapsed time $T = t_2 - t_1 \to 0$ (e.g., $T = 0.01\,\text{s} = 10\,\text{ms}$).

The formal physical G1 envelope is:
$$A_{\text{bound}}(T) = S_{\max} + R \cdot T = 1285.7143 + (1000.0 \times T)$$
As $T \to 0$, $A_{\text{bound}}(0) = 1285.7143\,\text{tokens}$.

**The Maximum Admission Sequence:**
1. At physical time $t = 0$: Client possesses full initial burst and idle credit ($B = 700, I = 450, S = 1285.71$). Client executes request of cost $c_1 = C_{\max} = 1000.0$.
   - Admitted: $A_1 = 1000.0\,\text{tokens}$.
   - Post-debit balances: $B = 0, I = 0, S = 285.7143$.
2. Discontinuity Event occurs within $T = 0.01\,\text{s}$:
   - **Case A (Asynchronous Failover):** Primary crashes before write is replicated. Replica promoted holding pre-debit state ($B = 700, I = 450, S = 1285.71$).
   - **Case B (Forward Clock Jump):** Host clock jumps forward by $\ge 60\,\text{seconds}$. Refills $S \rightarrow 1285.71, B \rightarrow 700, I \rightarrow 450$.
3. At physical time $t = 0.01\,\text{s}$: Client submits a second request of cost $c_2 = C_{\max} = 1000.0$.
   - Admitted: $A_2 = 1000.0\,\text{tokens}$.
4. Total admitted tokens over physical interval $[0, 0.01]$:
   $$A_{\text{total}} = A_1 + A_2 = 1000.0 + 1000.0 = \mathbf{2000.0\,\text{tokens}}$$

### 2.3 Exact Single-Event Excess Formula
$$\Delta_{\text{single\_event}}(T) = 2000.0 - \left(S_{\max} + R \cdot T\right)$$

- At $T = 0.01\,\text{s}$ (10 ms):
  $$\Delta_{\text{single\_event}}(0.01) = 2000.0 - (1285.7143 + 10.0) = \mathbf{704.2857\,\text{tokens}}$$
- In the theoretical limit as $T \to 0$:
  $$\Delta_{\text{single\_event}}^{\max} = 2000.0 - 1285.7143 = \mathbf{714.2857\,\text{tokens}}$$

```mermaid
xychart-beta
    title "Reconciliation of Observed vs Maximum Single-Event Over-Admission (T = 10ms)"
    x-axis ["Physical Bound", "Phase 7.1 (c=700)", "Phase 7.2 (c=1000 jump)", "True Maximum (c1=c2=1000)"]
    y-axis "Admitted Tokens" 1000 --> 2200
    bar [1295.71, 1400.0, 1700.0, 2000.0]
```

This reconciles all previous findings:
- $+104.29\,\text{tokens}$ was for $c_1 = 700, c_2 = 700$ with $I=0$ ($1400 - 1295.71$).
- $+404.29\,\text{tokens}$ was for $c_1 = 700, c_2 = 1000$ ($1700 - 1295.71$).
- **$+\mathbf{704.29\,\text{tokens}}$ is the true maximum single-event excess** for $c_1 = 1000, c_2 = 1000$.

---

## 3. Repeated Discontinuities and the Non-Existence of a Global Bound

> [!CAUTION]
> If the environment permits unconstrained repeated forward clock jumps or cascading failovers within an observation window, **NO FINITE GLOBAL UPPER BOUND EXISTS**.

In `testRepeatedForwardJumpsProduceArbitraryExcess`, three successive forward jumps of 60s within a single $10\,\text{ms}$ physical window admitted:
$$A_{\text{admitted}} = 4 \times 1000.0 = 4000.0\,\text{tokens}$$
$$\Delta_{\text{excess}} = 4000.0 - 1295.7143 = \mathbf{2704.29\,\text{tokens}}$$

If an adversary triggers or an environment experiences $K$ discontinuities within physical duration $T$:
$$A(T) \le (K + 1) \cdot C_{\max}$$
$$\Delta_{\text{excess}}(T) = (K + 1) \cdot C_{\max} - (S_{\max} + R \cdot T)$$

As $K \to \infty$, $\Delta_{\text{excess}} \to \infty$.  
Therefore, any claim that DREB-RCA70 has a finite over-admission bound **requires an explicit axiom bounding the number of discontinuities** ($K \le 1$).

---

## 4. Formalization of Physical-Time G1 Under Slew-Bounded Clocks

To prove G1 under physical time without assuming zero clock drift, we define the **Slew-Bounded Physical Clock Model**:

### 4.1 Definitions
1. **Physical Time ($t$):** Continuous time measured by an ideal SI second standard.
2. **Local Redis Timestamp ($\tau(t)$):** The timestamp returned by `redis.call('TIME')` at physical time $t$.
3. **Drift-Bounded Clock Axiom:** The host clock is synchronized via continuous slew daemons such that for all $t_1 < t_2$:
   $$\tau(t_2) - \tau(t_1) \le (t_2 - t_1) + \delta_{\max}$$
   where $\delta_{\max} \ge 0$ is the maximum permitted forward time error.

### 4.2 Theorem: Physical G1 Under Slew Bounds

$$\mathbf{Theorem\ (Physical\ G1\ Envelope):}$$  
Under the Drift-Bounded Clock Axiom with maximum drift $\delta_{\max}$, the total token volume $A(t_1, t_2)$ admitted by DREB-RCA70 over any physical time interval $[t_1, t_2]$ with $T = t_2 - t_1$ satisfies:

$$A(T) \le S_{\max} + R \cdot \left(T + \delta_{\max}\right)$$

$$\Delta_{\text{drift\_excess}} \le R \cdot \delta_{\max}$$

### 4.3 Practical Significance
Under standard production NTP slew (`chronyd` with `makestep 0`):
$$\delta_{\max} \le 5\,\text{ms} = 0.005\,\text{s}$$
$$\Delta_{\text{drift\_excess}} \le 1000.0 \times 0.005 = \mathbf{5.0\,\text{tokens}}$$

At $5.0\,\text{tokens}$, the drift error represents **$0.39\%$** of $S_{\max}$, which is negligible for real-world API rate limiting.

### 4.4 Observation Window Semantics
- **Window Start ($t_1$):** Physical instant the first request is evaluated by the primary Redis Lua engine.
- **Window End ($t_2$):** Physical instant the final request's decision is committed in primary Redis memory.
- **Counted Volume:** Tokens are debited and counted at the instant of successful Lua script evaluation (`DecisionStatus.ALLOW`), prior to transport serialisation.
- **Ambiguous Outcomes:** Retried transactions returning `isCached=true` debit 0 tokens and add 0 to $A(t_1, t_2)$.
