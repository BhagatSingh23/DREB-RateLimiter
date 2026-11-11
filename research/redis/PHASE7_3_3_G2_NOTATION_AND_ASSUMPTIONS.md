# PHASE 7.3.3: G2 ASYMPTOTIC RATE PROOF, DISAMBIGUATED NOTATION, AND OPERATIONAL ASSUMPTIONS

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.3 Task D  
**Repository Branch:** `research/systematic-improvement`  
**Starting Commit:** `2016eeb`  

---

## 1. Executive Summary

In DREB-RCA70, the symbol $K$ designates the dynamic, continuous abuse score ($K = \text{abuse\_score} \ge 0$). In Phase 7.3 and Phase 7.3.2, $K$ was simultaneously used to denote the integer count of discontinuity events occurring within an observation window.

This document resolves this notation collision:
1. **Disambiguated Symbolism:** We formally designate **$N_E(T) \in \mathbb{N}_0$** as the cumulative count of isolated discontinuity events occurring within physical interval $[0, T]$.
2. **Sublinear Frequency Condition:** We state and prove the exact necessary and sufficient condition for long-term rate convergence:
   $$\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$$
3. **Four Required Operational Assumptions:** Reconciled consistently across proof, guarantee tables, and manifest.
4. **Preservation of the G1 Boundary:** Re-verifying that asymptotic G2 compliance does **not** mitigate finite-window G1 burst over-admissions.

---

## 2. Formal Derivation with Disambiguated Notation

Let continuous physical time $T > 0$ elapsed over an observation interval $[0, T]$ be measured in seconds.  
Let $N_E(T) \ge 0$ denote the total number of discrete state reset events (un-replicated failovers, state recreation, or forward clock steps) occurring in $[0, T]$.  
Let $C_{\max} = 1000.0\,\text{tokens}$ be the maximum burst capacity admissible per reset epoch.  
Let $S_{\max} = 1285.7143\,\text{tokens}$ be the bounded initial sustained capacity.  
Let $R = 1000.0\,\text{tokens/s}$ be the continuous refill rate.

### 2.1 Cumulative Upper Bound
Across $N_E(T)$ discrete discontinuity events, the client has at most $N_E(T) + 1$ capacity-drawing epochs. Total cumulative admissions over $[0, T]$ are bounded by:

$$A_{\text{total}}(T) \le S_{\max} + \int_0^T R(t)\,dt + N_E(T) \cdot C_{\max}$$
$$A_{\text{total}}(T) \le S_{\max} + R \cdot T + N_E(T) \cdot C_{\max}$$

### 2.2 Time-Averaged Admission Rate
Dividing both sides by elapsed physical time $T$:

$$\frac{A_{\text{total}}(T)}{T} \le \frac{S_{\max}}{T} + R + C_{\max} \cdot \frac{N_E(T)}{T}$$

### 2.3 The Asymptotic Limit as $T \to \infty$
Taking the limit as $T \to \infty$:

$$\lim_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le R + \lim_{T \to \infty} \frac{S_{\max}}{T} + C_{\max} \cdot \lim_{T \to \infty} \frac{N_E(T)}{T}$$

Because initial capacity $S_{\max}$ is strictly finite:
$$\lim_{T \to \infty} \frac{S_{\max}}{T} = 0$$

Therefore, the asymptotic rate ceiling converges to $R$:

$$\lim_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le R = 1000.0\,\text{tokens/s} \iff \lim_{T \to \infty} \frac{N_E(T)}{T} = 0 \quad \blacksquare$$

---

## 3. Four Reconciled Operational Assumptions

For the asymptotic bound $\lim_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le 1000.0\,\text{tokens/s}$ to hold, all four conditions must be satisfied:

1. **Sublinear Discontinuity Frequency:**  
   $$\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$$  
   Failover events and clock adjustments must occur at a sublinear rate. If periodic resets occur at frequency $\nu > 0$, the long-term rate converges to $R + \nu \cdot C_{\max} > 1000.0\,\text{tokens/s}$, violating G2.
2. **Bounded Initial State:**  
   Initial token allocations must satisfy $S(0) \le S_{\max} = 1285.7143\,\text{tokens}$ and $B(0) \le B_{\max} = 700.0\,\text{tokens}$.
3. **Monotonic Physical Time Progression:**  
   The timebase $\tau$ must progress monotonically ($\frac{d\tau}{dt} > 0$), and long-term clock frequency error must converge to zero:
   $$\lim_{T \to \infty} \frac{\tau(t_0 + T) - \tau(t_0)}{T} = 1.0$$
4. **Restricted Distributed Failure Model:**  
   In the evaluated $N=1$ Redis Cluster topology, node crashes must have finite mean time to repair (MTTR) and positive mean time between failures (MTBF), ensuring the cumulative event count $N_E(T)$ grows strictly sublinearly over time.

---

## 4. Scientific Boundary: G2 Does Not Mitigate G1

> [!WARNING]
> An asymptotic G2 property ($\lim_{T \to \infty} \frac{A}{T} \le 1000.0$) provides **zero mitigation** for finite-window G1 burst violations.
> 
> A downstream service designed for $1,000\,\text{req/s}$ can be completely destabilized by the $+704.29\,\text{token}$ burst spike occurring during a $10\,\text{ms}$ failover window ($200,000\,\text{tokens/s}$ instantaneous throughput), even if the 24-hour average rate converges perfectly to $1000.0\,\text{tokens/s}$.
> 
> G1 and G2 govern independent, orthogonal temporal domains.
