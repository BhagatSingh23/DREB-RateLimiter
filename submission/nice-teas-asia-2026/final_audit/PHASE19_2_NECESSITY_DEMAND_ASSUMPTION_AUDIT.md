# PHASE 19.2: FINAL NECESSITY-THEOREM DEMAND ASSUMPTION AUDIT

**Repository:** `BhagatSingh23/DREB-RateLimiter`  
**Branch:** `submission/nice-teas-asia-2026`  
**Base Commit:** `e705dc0`  
**Audit Date:** 15 November 2026  
**Final Verdict:** `A. NECESSITY PROOF CLOSED UNDER EXPLICIT STRONGER DEMAND ASSUMPTIONS.`

---

## 1. Demand-Rate Audit: Offered vs. Admitted Rates

### 1.1 The Mathematical Discrepancy in Phase 19.1
In Phase 19.1, the saturated adversarial demand condition was characterized by:
$$\Delta t_{\text{arr}} \le \frac{1}{R} = 1.0\,\text{ms}$$
which corresponds to an offered request rate:
$$\lambda_{\text{offered}} \ge R = 1000.0\,\text{requests/s}$$

However, the subsequent derivation of sustained-token depletion asserted that during the post-burst drain phase, requests are admitted at:
$$\lambda_{\text{admitted}} = r_B = \frac{5}{3} R \approx 1666.67\,\text{tokens/s}$$
so that the sustained bucket drains at net rate:
$$\frac{dS}{dt} = R - r_B = R - \frac{5}{3} R = -\frac{2}{3} R = -666.67\,\text{tokens/s}$$

### 1.2 Inspection of Implementation Mechanics
We inspected the request evaluation pipeline in `src/main/java/com/dreb/ratelimit/`:
1. **`AbstractRateLimiter.allow(...)`**:
   $$\text{elapsed } dt = \text{now} - \text{state.lastRefillTs}$$
   Calls `refill(state, dt)`, then `checkAndConsume(state, cost)`.
2. **`ConstantRateRefillStrategy.refill(...)`**:
   $$S \leftarrow \min(S_{\max}, S + dt \times R)$$
   $$B \leftarrow \min(B_{\max}, B + dt \times r_B)$$
3. **`DREBLimiter.checkAndConsume(...)`**:
   ```java
   double effectiveBurst = state.burstTokens + idlePolicy.effectiveCredit(state);
   if (state.sustainedTokens >= cost && effectiveBurst >= cost) {
       state.sustainedTokens -= cost;
       state.burstTokens = Math.max(0.0, state.burstTokens - cost);
       idlePolicy.spend(state, deficit);
       return Decision.ALLOW;
   }
   return Decision.DENY;
   ```
4. **Fundamental Physical Invariant:**
   The rate of admitted requests cannot exceed the rate of offered requests:
   $$\lambda_{\text{admitted}}(t) \le \lambda_{\text{offered}}(t)$$
   If the client offers only $\lambda_{\text{offered}} = R = 1000.0\,\text{requests/s}$, the limiter **cannot** admit $1666.67\,\text{requests/s}$.
   At $\lambda_{\text{admitted}} = 1000.0\,\text{requests/s}$, the sustained bucket refills at rate $R = 1000.0\,\text{tokens/s}$, yielding:
   $$\frac{dS}{dt} = R - \lambda_{\text{admitted}} = 1000.0 - 1000.0 = 0.0\,\text{tokens/s}$$
   **Finding:** The condition $\Delta t_{\text{arr}} \le 1/R$ does **not** guarantee sustained-token depletion. A strictly stronger offered-demand condition is mathematically required.

---

## 2. Concrete Counterexample: Offered Demand at Exactly $1000\,\text{requests/s}$

We trace an adversarial workload offering exactly unit-cost requests at interval $\Delta t = 1.0\,\text{ms}$ ($\lambda_{\text{offered}} = 1000.0\,\text{requests/s}$) under DREB-RCA70 ($C=1000, R=1000, B_{\max}=700, S_{\max}=1285.7143, r_B=1666.6667$).

### 2.1 State Evolution Trace
1. **Initial State ($t = 0$):**
   $B(0) = 700.0, \quad S(0) = 1285.7143, \quad I(0) = 0.0$.
2. **Instantaneous Burst ($t = 0$):**
   The adversary submits 700 requests instantaneously.
   All 700 are admitted ($B \ge 700, S \ge 700$).
   Post-burst state at $t = 0^+$:
   $$B(0^+) = 0.0, \quad S(0^+) = 1285.7143 - 700.0 = 585.7143, \quad I(0^+) = 0.0$$
3. **Continuous Offered Demand for $t > 0$ ($\lambda_{\text{offered}} = 1000.0\,\text{req/s}$):**
   At $t = 1.0\,\text{ms}$:
   - Refill of $S$: $\Delta S = 1000.0 \times 0.001 = 1.0\,\text{token} \implies S = 585.7143 + 1.0 = 586.7143$.
   - Refill of $B$: $\Delta B = 1666.6667 \times 0.001 = 1.6667\,\text{tokens} \implies B = 0.0 + 1.6667 = 1.6667$.
   - Request evaluation: $S \ge 1$ and $B \ge 1 \implies$ **ALLOW**.
   - Debit: $S \leftarrow 586.7143 - 1.0 = 585.7143$, $B \leftarrow 1.6667 - 1.0 = 0.6667$.
4. **Long-Run Balance Dynamics:**
   Across every $1.0\,\text{ms}$ step, $1.0$ sustained token refills and $1.0$ sustained token is consumed.
   $$\frac{dS}{dt} = 0 \implies S(t) \equiv 585.7143\,\text{tokens for all } t > 0$$
   $S(t)$ **never drains to zero**.
   Meanwhile, burst tokens replenish faster than consumption:
   $$\frac{dB}{dt} = r_B - \lambda_{\text{admitted}} = 1666.6667 - 1000.0 = +666.6667\,\text{tokens/s}$$
   $B(t)$ accumulates until reaching $B_{\max} = 700.0$ at $t = \frac{700.0}{666.6667} = 1.050\,\text{seconds}$.
   For $t \ge 1.050\,\text{s}$, burst tokens overflow at rate $+666.67\,\text{tokens/s}$, while $S(t)$ remains frozen at $585.7143$.

### 2.2 Consequence for the Next Effective Event
Suppose a discontinuity event occurs at $t_1 > 1.050\,\text{s}$:
- Immediately before event: $S(t_1^-) = 585.7143, \quad B(t_1^-) = 700.0$.
- If the node rolls back to a cold snapshot where $S(t_1^+) = 700.0$:
  The actual sustained-token jump is:
  $$\Delta S_1^{\text{jump}} = S(t_1^+) - S(t_1^-) = 700.0 - 585.7143 = \mathbf{114.2857\,\text{tokens}} \ll 700.0\,\text{tokens}$$
- Most critically, under $\lambda_{\text{offered}} = 1000.0\,\text{requests/s}$, total admissions can **never** exceed offered traffic:
  $$A(T) \le \lambda_{\text{offered}} \cdot T = 1000.0 \cdot T \implies \frac{A(T)}{T} \le R = 1000.0\,\text{tokens/s}$$
  Even with arbitrarily high event frequency $\lambda_E^* \gg 0$, **the macroscopic rate never exceeds $R$**!
- **Rigorous Takeaway:** Saturated demand at $\lambda_{\text{offered}} = R$ is insufficient to establish necessity. The necessity theorem must explicitly assume high-rate adversarial demand ($\lambda_{\text{offered}} > R$).

---

## 3. Stronger Demand Assumption: Formulation & Proof

### 3.1 Minimum Adversarial Demand Condition
To guarantee sustained-token depletion ($S(t_k^-) \to 0$), the offered rate must strictly exceed $R$.
To deplete $S$ at the maximum rate supported by the limiter's dual-bucket architecture, the adversary must offer at least the burst bucket refill rate:
$$\boxed{\lambda_{\text{offered}} \ge r_B = \frac{5}{3} R \approx 1666.67\,\text{requests/s}} \quad \left(\Delta t_{\text{arr}} \le \frac{3}{5R} = 0.60\,\text{ms}\right)$$

### 3.2 Proof of Admission Feasibility While $S(t) > 0$
We prove that `DREBLimiter` can admit requests at rate $r_B = \frac{5}{3} R$ throughout the drain phase:
1. **Burst Feasibility:** The burst bucket refills at rate $r_B = \frac{5}{3} R$. If $\lambda_{\text{offered}} \ge r_B$, backlogged requests arrive at least as fast as burst tokens replenish. Whenever $B \ge 1$ (every $0.60\,\text{ms}$), a request arrives.
2. **Sustained Feasibility:** The sustained bucket starts post-burst at $S = S_{\max} - B_{\max} = 585.7143 > 0$.
3. **Joint Conjunction:** In `checkAndConsume`:
   $$\text{cost} = 1.0 \le S(t) \quad \text{and} \quad \text{cost} = 1.0 \le B(t) + I(t)$$
   Both constraints are satisfied for every request.
4. **Admitted Rate:**
   $$\lambda_{\text{admitted}} = \min(\lambda_{\text{offered}}, r_B) = r_B = \frac{5}{3} R = 1666.67\,\text{requests/s}$$
5. **Sustained Drain Rate:**
   $$\frac{dS}{dt} = R - \lambda_{\text{admitted}} = R - \frac{5}{3} R = -\frac{2}{3} R = -666.67\,\text{tokens/s} < 0$$

### 3.3 Minimum Active-Serving Separation $\delta$
The time required to drain $S(t)$ from $S_{\max} - B_{\max}$ down to zero at drain rate $\frac{2}{3} R$ is:
$$t_{\text{drain}}^{\min} = \frac{S_{\max} - B_{\max}}{\frac{2}{3} R} = \frac{\frac{9}{7} C - \frac{7}{10} C}{\frac{2}{3} R} = \frac{\frac{41}{70} C}{\frac{2}{3} R} = \frac{123}{140} \frac{C}{R} \approx 0.878571\,\text{seconds}$$

For any effective event to guarantee an independent state injection $\Delta S_k^{\text{jump}} \ge c_{\min}$:
$$\text{Active serving separation } |I_{k-1}| \ge t_{\text{drain}}^{\min} = \frac{123}{140} \frac{C}{R} \approx 0.88\,\text{seconds}$$
Under this separation and $\lambda_{\text{offered}} \ge \frac{5}{3} R$, $S(t_k^-) \in [0, 1) \approx 0$ is rigorously guaranteed.

---

## 4. Rigorous Sustained Overflow Proof

We re-derive the cumulative overflow term under the corrected high-rate demand assumption ($\lambda_{\text{offered}} \ge \frac{5}{3} R$).

### 4.1 Sustained-Bucket Overflow ($\sum \text{Overflow}_{S, k}$)
1. **Drain Phase ($t \in [t_k^{\text{start}}, t_k^{\text{start}} + t_{\text{drain}}]$):**
   - $S(t)$ strictly decreases from $585.7143$ to $0.0$.
   - $S(t) \le 585.7143 < S_{\max} \approx 1285.7143$ at all times.
   - Refill does not exceed capacity; $\text{Overflow}_{S} = 0$.
2. **Steady-State Phase ($t \in (t_k^{\text{start}} + t_{\text{drain}}, t_k^{\text{end}}]$):**
   - $S(t)$ is depleted ($S \approx 0$).
   - Sustained tokens refill at rate $R = 1000.0\,\text{tokens/s}$ (1 token per $1.0\,\text{ms}$).
   - Since $\lambda_{\text{offered}} \ge \frac{5}{3} R \approx 1666.67\,\text{req/s}$, requests arrive every $\le 0.60\,\text{ms}$.
   - As soon as $S \ge 1.0$, a request arrives, passes `checkAndConsume`, and debits $S$ back to $0$.
   - $S(t) \in [0, 1) \ll S_{\max}$ at all times.
   - Zero sustained tokens accumulate towards $S_{\max}$; $\text{Overflow}_{S} = 0$.
3. **Discontinuity Boundaries:**
   - During transition or abort downtime, the node is offline. Elapsed time is not refilled into sustained tokens.
   - Node restart or replica promotion assigns balances directly without continuous overflow.
4. **Conclusion on Sustained Overflow:**
   $$\boxed{\sum_{k=0}^{N_E^*} \text{Overflow}_{S, k} \equiv 0}$$

### 4.2 Burst-Bucket Overflow vs. Sustained Accounting
- In the steady-state phase, burst tokens refill at $r_B = 1666.67\,\text{tokens/s}$, while admissions are throttled by sustained tokens to $R = 1000.0\,\text{tokens/s}$.
- Burst tokens accumulate at rate $+666.67\,\text{tokens/s}$ and reach $B_{\max} = 700.0$ in $1.050\,\text{s}$, after which excess burst tokens overflow.
- **Independence:** The Global Accounting Identity governs macroscopic rate containment strictly through the **sustained token balance** $S(t)$. Burst bucket overflow has **zero** effect on $S(t)$ or $A(T)$.

### 4.3 Rejected Requests
- During steady state, the adversary offers $\ge 1666.67\,\text{req/s}$ while the limiter admits $1000.0\,\text{req/s}$.
- The excess $\ge 666.67\,\text{req/s}$ are rejected (`Decision.DENY`).
- In `DREBLimiter.checkAndConsume`, a `DENY` decision performs no state mutation (`state.sustainedTokens` is untouched). Rejected requests generate zero token debits and zero token overflow.

---

## 5. Corrected Necessity Theorem Statement

With all required physical and workload assumptions made explicit, Theorem 4 is stated as follows:

### Theorem 4 (G2 Asymptotic Rate Containment Criteria)
The long-run macroscopic admission rate is contained by SLA contracted rate $R$:
$$\limsup_{T\to\infty} \frac{A(T)}{T} \le R$$
1. **Universal Sufficiency:**
   Sublinear total event frequency:
   $$\lim_{T\to\infty} \frac{N_E(T)}{T} = 0$$
   is **universally sufficient** under all admissible arrival patterns, arbitrary downtime distributions, and arbitrary initial states.
2. **Conditional Necessity:**
   Under the joint conditions of:
   - **High-rate saturated adversarial demand:** $\lambda_{\text{offered}}(t) \ge \frac{5}{3} R$ continuously during active serving;
   - **Asymptotically negligible abort downtime:** $\lim_{T\to\infty} \frac{\mu(\Omega_{\text{abort}}(T))}{T} = 0$;
   - **Independently exploitable effective events:** $N_E^*(T)$ counts events with uncoordinated state amnesia ($S(t_k^+) \ge c_{\min} \ge 700\,\text{tokens}$) separated by active serving uptime $\delta \ge t_{\text{drain}}^{\min} = \frac{123}{140} \frac{C}{R} \approx 0.88\,\text{seconds}$;
   
   sublinear effective event frequency:
   $$\lim_{T\to\infty} \frac{N_E^*(T)}{T} = 0$$
   is **strictly necessary**.

### Proof of Lower Bound under Corrected Assumptions:
Under $\lambda_{\text{offered}} \ge \frac{5}{3} R$ and $|I_{k-1}| \ge t_{\text{drain}}^{\min}$, $S(t_k^-) \in [0, 1) \approx 0$, so $\Delta S_k^{\text{jump}} \ge c_{\min} \ge 700$. Saturated demand ensures $\sum \text{Overflow}_{S, k} = 0$.
$$A(T) \ge R \cdot T_{\text{normal}} + N_E^*(T) \cdot c_{\min} - S_{\max}$$
Substituting $T_{\text{normal}} \ge T - N_E^*(T) \cdot \Delta t_{\text{event}} - \mu(\Omega_{\text{abort}}(T))$:
$$A(T) \ge R \cdot T + N_E^*(T) \cdot (c_{\min} - R \cdot \Delta t_{\text{event}}) - R \cdot \mu(\Omega_{\text{abort}}(T)) - S_{\max}$$
Dividing by $T$ and evaluating $\limsup_{T \to \infty}$ along a subsequence where $\frac{N_E^*(T_k)}{T_k} \to \lambda_E^* > 0$:
$$\limsup_{T\to\infty} \frac{A(T)}{T} \ge R + \lambda_E^*(c_{\min} - R \cdot \Delta t_{\text{event}}) > R$$
Because $c_{\min} \ge 700 > R \cdot \Delta t_{\text{event}} = 1000 \times 0.010 = 10\,\text{tokens}$, the net excess $\lambda_E^*(690) > 0$ strictly violates rate containment.

---

## 6. Manuscript Consistency and Page Budgets

Both manuscripts were updated in `latex/main.tex` and `compact-8-page/latex/main.tex` to explicitly specify the high-rate demand condition ($\lambda_{\text{offered}} \ge \frac{5}{3} R$) and sustained-overflow elimination ($\sum \text{Overflow}_{S, k} = 0$).

Recompilation with Tectonic 0.17.0 confirms:
- **`submission/nice-teas-asia-2026/main.pdf`**: **Exactly 10 pages** (Verified via `pypdf 7.0.1`).
- **`submission/nice-teas-asia-2026/compact-8-page/main.pdf`**: **Exactly 8 pages** (Verified via `pypdf 7.0.1`).

---

## 7. Deliverable Verdict

$$\boxed{\textbf{A. NECESSITY PROOF CLOSED UNDER EXPLICIT STRONGER DEMAND ASSUMPTIONS.}}$$

The mathematical necessity proof is fully established with all workload, rate, and system-state assumptions explicitly stated and verified against actual implementation mechanics.
