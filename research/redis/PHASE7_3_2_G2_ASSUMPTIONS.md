# PHASE 7.3.2: G2 ASYMPTOTIC RATE SCOPE, FORMAL ASSUMPTIONS, AND G1 BOUNDARY

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.2 Task E  
**Repository Branch:** `research/systematic-improvement`  
**Starting Commit:** `5578582`  

---

## 1. Executive Summary

Guarantee G2 governs long-term sustained throughput, asserting that as observation duration approaches infinity, the average admission rate does not exceed the nominal refill rate $R = 1000.0\,\text{tokens/s}$:

$$\lim_{T \to \infty} \frac{A(T)}{T} \le R = 1000.0\,\text{tokens/s}$$

This document formalizes the rigorous mathematical scope of G2:
1. **Explicit Assumptions:** G2 holds **if and only if** four explicit physical and operational conditions are satisfied.
2. **Failure of G2 Under Periodic Discontinuities:** If state resets or forward clock jumps occur at a non-zero frequency $\nu > 0$, the asymptotic rate ceiling is violated.
3. **The G1 vs. G2 Boundary:** Asymptotic convergence over long horizons ($T \to \infty$) **does not repair, mitigate, or excuse finite-window G1 burst violations**.

---

## 2. Formal Derivation of Asymptotic G2

Let continuous physical time elapsed over an observation window be $T > 0$.  
Let $A(T)$ be the total cumulative tokens admitted over the interval $[0, T]$.  
In the general case with $K(T)$ discrete state reset events occurring during $[0, T]$:

$$A(T) \le S_{\max} + \int_0^T R(t)\,dt + \sum_{k=1}^{K(T)} C_{\max}$$
$$A(T) \le S_{\max} + R \cdot T + K(T) \cdot C_{\max}$$

Dividing by $T$:

$$\frac{A(T)}{T} \le \frac{S_{\max}}{T} + R + \frac{K(T) \cdot C_{\max}}{T}$$

Taking the limit as $T \to \infty$:

$$\lim_{T \to \infty} \frac{A(T)}{T} \le R + \lim_{T \to \infty} \frac{S_{\max}}{T} + C_{\max} \cdot \lim_{T \to \infty} \frac{K(T)}{T}$$

Because $S_{\max} = 1285.7143$ is finite:
$$\lim_{T \to \infty} \frac{S_{\max}}{T} = 0$$

Therefore:

$$\lim_{T \to \infty} \frac{A(T)}{T} \le R \iff \lim_{T \to \infty} \frac{K(T)}{T} = 0$$

---

## 3. Mandatory Operational Assumptions

For the asymptotic bound $\lim_{T \to \infty} \frac{A(T)}{T} \le 1000.0\,\text{tokens/s}$ to hold, the following four conditions must be met:

### Assumption 1: Sublinear Discontinuity Frequency
The number of state resets (un-replicated failovers, state evictions, or forward clock jumps) within interval $T$ must grow sublinearly with time:
$$\lim_{T \to \infty} \frac{K(T)}{T} = 0$$
*Pathological Counterexample:* If an infrastructure flaw or periodic script triggers a failover or clock jump every $\tau_{\text{period}} = 10\,\text{seconds}$, the event frequency is $\nu = 0.1\,\text{s}^{-1}$. The asymptotic rate becomes:
$$\lim_{T \to \infty} \frac{A(T)}{T} \le 1000.0 + (0.1 \times 1000.0) = \mathbf{1100.0\,\text{tokens/s}} \quad (\text{Violation})$$

### Assumption 2: Bounded Initial State
The initial token balances must be bounded:
$$S(0) \le S_{\max} = 1285.7143\,\text{tokens}, \quad B(0) \le B_{\max} = 700.0\,\text{tokens}, \quad I(0) \le I_{\max} = 450.0\,\text{tokens}$$

### Assumption 3: Valid Physical Time Progression
The perceived timebase $\tau$ must progress monotonically without backward steps ($\frac{d\tau}{dt} > 0$), and the long-term drift rate must approach zero:
$$\lim_{T \to \infty} \frac{\tau(t + T) - \tau(t)}{T} = 1.0$$

### Assumption 4: Restricted Distributed Failure Model
Under the validated Redis Cluster model ($N=1$), single primary node crashes must be transient and separated by sufficient recovery intervals such that the total number of un-replicated crash events over infinite time has zero measure relative to $T$.

---

## 4. The G1 vs. G2 Scientific Boundary

> [!CAUTION]
> **Asymptotic Convergence Does NOT Protect Downstream Infrastructure Against Short-Term Bursts.**

In rate limiting research, claims of asymptotic compliance ($G_2$) are sometimes improperly invoked to downplay finite-window burst violations ($G_1$). We explicitly reject this conflation:

1. **Downstream Failure Modes are Short-Term:** Database connection pools, thread executors, and upstream microservices fail during millisecond-to-second bursts ($T \in [10\,\text{ms}, 1\,\text{s}]$), not over infinite horizons.
2. **Magnitude of the Spike:** As proved in Phase 7.3.2 Task B, a single failover permits up to **$2000.0\,\text{tokens}$** to be admitted in a $10\,\text{ms}$ physical window.
3. **Mathematical Separation:**
   - Over a $10\,\text{ms}$ window: Average rate is $\frac{2000.0}{0.010} = \mathbf{200,000\,\text{tokens/s}}$ ($200 \times$ nominal capacity).
   - Over a $60\,\text{second}$ window: Average rate converges to $\approx 1011\,\text{tokens/s}$ ($+1.1\%$).
   - Over an infinite window: Average rate converges to $1000.0\,\text{tokens/s}$ ($+0.0\%$).

**Conclusion:**  
Guarantee G2 is an essential long-term capacity property, but **provides zero mathematical or operational mitigation for short-term G1 burst over-admissions.**
