# PHASE 19.3: FINAL MATHEMATICAL CONSISTENCY & EVENT SEPARATION CORRECTION

**Repository:** `BhagatSingh23/DREB-RateLimiter`  
**Branch:** `submission/nice-teas-asia-2026`  
**Base Commit:** `b8686d2`  
**Audit Date:** 15 November 2026  
**Final Verdict:** `A. NECESSITY PROOF CLOSED WITH EXPLICIT INITIAL-BURST AND SEPARATION CONDITIONS.`

---

## 1. Issue 1: Initial Burst Assumption vs. Continuous Arrival

### 1.1 The Analytical Dilemma
In Phase 19.2, the sustained-bucket depletion duration was derived as:
$$t_{\text{drain}} = \frac{S_{\max} - B_{\max}}{r_B - R} = \frac{1285.7143 - 700.0}{1666.6667 - 1000.0} = \frac{585.7143}{666.6667} = 0.878571\,\text{seconds}$$

This specific numerical duration relies on the premise that an instantaneous burst of $B_{\max} = 700$ requests is submitted and admitted at timestamp $t_k^{\text{start}}$, reducing the sustained balance from $S_{\max}$ to $S_{\max} - B_{\max}$ prior to continuous drain.

However, the continuous arrival assumption $\lambda_{\text{offered}} \ge r_B = \frac{5}{3} R \approx 1666.67\,\text{req/s}$ only bounds the request inter-arrival time by $\Delta t_{\text{arr}} \le 0.60\,\text{ms}$; it does not by itself guarantee that 700 requests arrive simultaneously at $t_k^{\text{start}}$.

### 1.2 Derivation of Case A: Instantaneous Initial Burst Batch
- **Workload Model:** At timestamp $t_k^{\text{start}}$, the adversary submits an instantaneous batch of $B_{\max} = 700$ unit-cost requests, followed immediately by continuously backlogged demand at $\lambda_{\text{offered}} \ge r_B = \frac{5}{3} R$.
- **Instantaneous Admission:**
  At $t_k^{\text{start}}$, $B = 700.0$ and $S = 1285.7143$. All 700 requests are admitted at $t_k^{\text{start}}$.
  Post-burst state at $t_k^{\text{start}+}$:
  $$B(t_k^{\text{start}+}) = 0.0, \quad S(t_k^{\text{start}+}) = S_{\max} - B_{\max} = 585.7143\,\text{tokens}$$
- **Continuous Drain Phase ($t > t_k^{\text{start}}$):**
  Burst tokens refill at rate $r_B = \frac{5}{3} R$. Backlogged requests consume burst tokens as fast as they replenish ($\lambda_{\text{admitted}} = r_B = \frac{5}{3} R$).
  Sustained tokens refill at rate $R$.
  Net drain rate of sustained tokens:
  $$\frac{dS}{dt} = R - r_B = R - \frac{5}{3} R = -\frac{2}{3} R = -666.6667\,\text{tokens/s}$$
- **Depletion Duration:**
  $$t_{\text{drain}}^{\text{burst}} = \frac{S_{\max} - B_{\max}}{\frac{2}{3} R} = \frac{\frac{41}{70} C}{\frac{2}{3} R} = \frac{123}{140} \frac{C}{R} = \mathbf{0.878571\,\text{seconds}}$$
- **Required Active Serving Separation:**
  $$\delta_A \ge t_{\text{drain}}^{\text{burst}} = \frac{123}{140} \frac{C}{R} \approx \mathbf{0.88\,\text{seconds}}$$

### 1.3 Derivation of Case B: Smooth Continuous Demand Without Initial Batch Burst
- **Workload Model:** The adversary offers unit-cost requests smoothly at rate $\lambda_{\text{offered}} = r_B = \frac{5}{3} R \approx 1666.67\,\text{requests/s}$ ($\Delta t_{\text{arr}} \approx 0.60\,\text{ms}$) with no initial batch burst.
- **Initial State at $t_k^{\text{start}}$:**
  $B(t_k^{\text{start}}) = B_{\max} = 700.0, \quad S(t_k^{\text{start}}) = S_{\max} = 1285.7143$.
- **Burst Bucket Evolution:**
  Requests arrive at rate $1666.67\,\text{req/s}$.
  Burst tokens refill at rate $r_B = 1666.67\,\text{tokens/s}$.
  Admitted requests debit burst tokens at rate $1666.67\,\text{tokens/s}$.
  Net rate of change of burst tokens:
  $$\frac{dB}{dt} = r_B - \lambda_{\text{admitted}} = 1666.6667 - 1666.6667 = 0.0\,\text{tokens/s}$$
  Therefore:
  $$B(t) \equiv 700.0\,\text{tokens} \ge 1.0 \quad \text{for all } t \in [t_k^{\text{start}}, t_k^{\text{start}} + t_{\text{drain}}^{\text{smooth}}]$$
  The burst-token constraint ($B \ge 1$) is **strictly satisfied** throughout the entire drain interval without a single rejection.
- **Sustained Bucket Evolution:**
  $S(t)$ starts at $S_{\max} = 1285.7143\,\text{tokens}$ (undiminished by any initial batch).
  Sustained tokens refill at rate $R = 1000.0\,\text{tokens/s}$.
  Requests debit sustained tokens at rate $\lambda_{\text{admitted}} = 1666.67\,\text{tokens/s}$.
  Net rate of change of sustained tokens:
  $$\frac{dS}{dt} = R - r_B = R - \frac{5}{3} R = -\frac{2}{3} R = -666.6667\,\text{tokens/s}$$
- **Depletion Duration From $S_{\max}$:**
  $$t_{\text{drain}}^{\text{smooth}} = \frac{S_{\max}}{\frac{2}{3} R} = \frac{\frac{9}{7} C}{\frac{2}{3} R} = \frac{27}{14} \frac{C}{R} = \mathbf{1.928571\,\text{seconds}}$$
- **Required Active Serving Separation:**
  $$\delta_B \ge t_{\text{drain}}^{\text{smooth}} = \frac{27}{14} \frac{C}{R} \approx \mathbf{1.93\,\text{seconds}}$$

### 1.4 Synthesis and Alignment with Effective Events
- An **effective event** in the paper is defined as an uncoordinated state discontinuity that provides an independently exploitable burst opportunity ($c_{\min} \ge 700\,\text{tokens}$).
- An adversarial client exploiting this burst opportunity submits an initial batch burst of $700$ requests immediately upon detecting service readiness, satisfying **Case A** ($\delta \ge 0.88\,\text{s}$).
- If an adversary does not fire an instantaneous batch but sends requests at maximum rate $\lambda_{\text{offered}} \ge \frac{5}{3} R$, **Case B** applies ($\delta \ge 1.93\,\text{s}$).
- Both conditions guarantee complete sustained depletion ($S(t_k^-) \approx 0$). In the formal theorem, we explicitly state both conditions.

---

## 2. Issue 2: Steady-State Sustained Overflow Bound ($S(t) < 1.60\,\text{tokens}$)

We rigorously verify the steady-state sustained token balance under unit-cost requests arriving at intervals $\Delta t \le 0.60\,\text{ms}$ ($\lambda_{\text{offered}} \ge \frac{5}{3} R$).

### 2.1 State Evolution in `AbstractRateLimiter.java`
Each request executes:
```java
double dt = now - state.lastRefillTs;
refill(state, dt);
state.lastRefillTs = now;
Decision decision = checkAndConsume(state, cost);
store.save(clientId, state);
```

### 2.2 Mathematical Bounding of $S(t)$
1. **Refill Increment:**
   With inter-arrival gap $\Delta t \le 0.60\,\text{ms}$, the maximum refill accumulated between successive request evaluations is:
   $$\Delta S_{\text{refill}} = \Delta t \times R \le 0.00060\,\text{s} \times 1000.0\,\text{tokens/s} = 0.60\,\text{tokens}$$
2. **Post-Depletion Inductive State Bound:**
   Once $S(t)$ has drained to zero, consider any request evaluation at timestamp $t_n$:
   - **Case 1: Previous request at $t_{n-1}$ was DENIED.**
     A request is denied if and only if $S(t_{n-1}) < 1.0\,\text{token}$.
     In `checkAndConsume`, a `DENY` performs no debit. `store.save` persists the state with $S(t_{n-1}^+) < 1.0$ and updates `lastRefillTs = t_{n-1}`.
     At timestamp $t_n = t_{n-1} + \Delta t$, elapsed time is $dt \le 0.60\,\text{ms}$.
     The balance immediately after refill is:
     $$S(t_n^-) = S(t_{n-1}^+) + dt \times R < 1.0 + 0.60 = \mathbf{1.60\,\text{tokens}}$$
     If $S(t_n^-) \ge 1.0$, request $n$ is **ALLOWED**, and $S$ is debited by $1.0$:
     $$S(t_n^+) = S(t_n^-) - 1.0 < 1.60 - 1.0 = 0.60\,\text{tokens} \in [0, 0.60)$$
     If $S(t_n^-) < 1.0$, request $n$ is **DENIED**, and $S(t_n^+) = S(t_n^-) < 1.0\,\text{token}$.
   - **Case 2: Previous request at $t_{n-1}$ was ALLOWED.**
     Then $S(t_{n-1}^+) < 1.60 - 1.0 = 0.60\,\text{tokens}$.
     At $t_n$, refill adds at most $0.60$ tokens:
     $$S(t_n^-) = S(t_{n-1}^+) + dt \times R < 0.60 + 0.60 = 1.20\,\text{tokens} < 1.60\,\text{tokens}$$
     If allowed, $S(t_n^+) < 0.20\,\text{tokens}$.
3. **Universal Peak Bound:**
   At all times $t$ during steady-state normal serving:
   $$\boxed{S(t) < 1.60\,\text{tokens}}$$

### 2.3 Elimination of Sustained-Bucket Overflow
In `ConstantRateRefillStrategy.refill`:
`state.sustainedTokens = Math.min(sustainedMax, state.sustainedTokens + dt * sustainedRate);`
Sustained overflow occurs if and only if:
$$S(t) + dt \times R > S_{\max} = \frac{9}{7} C \approx 1285.7143\,\text{tokens}$$
Since $S(t) < 1.60 \ll 1285.7143$ at every evaluation, the sustained balance is separated from capacity by more than $1284$ tokens.
Therefore, **cumulative sustained overflow is identically zero**:
$$\boxed{\sum_{k=0}^{N_E^*} \text{Overflow}_{S, k} \equiv 0}$$

### 2.4 Burst-Bucket Overflow Separation
- During steady state, burst tokens accumulate at rate $+666.67\,\text{tokens/s}$ because $r_B = 1666.67 > R = 1000$. Burst tokens reach $B_{\max} = 700$ and overflow.
- However, burst tokens do not enter the sustained-token Global Accounting Identity. Macroscopic rate containment is governed strictly by the sustained token balance $S(t)$. Burst overflow has zero effect on $S(t)$ or $A(T)$.

---

## 3. Issue 3: Corrected Necessity Theorem

### Complete Specification of Necessary Conditions:
1. **Request Cost:** Unit cost ($cost = 1.0$).
2. **Adversarial Offered Demand:** High-rate demand offering $\lambda_{\text{offered}} \ge \frac{5}{3} R \approx 1666.67\,\text{req/s}$ ($\Delta t_{\text{arr}} \le 0.60\,\text{ms}$).
3. **Initial Burst & Active Event Separation:**
   - Either: Adversary fires an instantaneous batch of $B_{\max} = 700$ requests upon node readiness, with active serving separation:
     $$\delta \ge t_{\text{drain}}^{\text{burst}} = \frac{123}{140} \frac{C}{R} \approx 0.88\,\text{seconds}$$
   - Or: Adversary sends smooth continuous traffic at $\lambda_{\text{offered}} \ge \frac{5}{3} R$ without an initial batch, with active serving separation:
     $$\delta \ge t_{\text{drain}}^{\text{smooth}} = \frac{27}{14} \frac{C}{R} \approx 1.93\,\text{seconds}$$
4. **State Reset Behavior:** Uncoordinated state amnesia restoring $S(t_k^+) \ge c_{\min} \ge 700\,\text{tokens}$ and $B(t_k^+) \ge 700\,\text{tokens}$.
5. **Event Transition Duration:** Finite duration $\Delta t_j \le \Delta t_{\text{event}} < c_{\min} / R$ (e.g. $\Delta t_{\text{event}} = 10\,\text{ms}$, where $R \cdot \Delta t_{\text{event}} = 10 \ll 700$).
6. **Abort Downtime:** Asymptotically negligible: $\lim_{T \to \infty} \frac{\mu(\Omega_{\text{abort}}(T))}{T} = 0$.
7. **Per-Event Additional Admitted Capacity:**
   Because $S(t_k^-) < 1.0$, the event state injection provides:
   $$\Delta S_k^{\text{jump}} = S(t_k^+) - S(t_k^-) \ge c_{\min} - 1.0 \approx c_{\min} \ge 700\,\text{tokens}$$
   Net excess over forfeited transition refill:
   $$\Delta A_k^{\text{net}} = c_{\min} - R \cdot \Delta t_{\text{event}} \ge 700 - 1000 \times 0.010 = +690\,\text{tokens} > 0$$

### Corrected Lower Bound Derivation:
$$A(T) \ge R \cdot T_{\text{normal}} + N_E^*(T) \cdot c_{\min} - S_{\max}$$
Using $T_{\text{normal}} \ge T - N_E^*(T) \cdot \Delta t_{\text{event}} - \mu(\Omega_{\text{abort}}(T))$:
$$A(T) \ge R \cdot T + N_E^*(T) \cdot (c_{\min} - R \cdot \Delta t_{\text{event}}) - R \cdot \mu(\Omega_{\text{abort}}(T)) - S_{\max}$$
Dividing by $T$ and evaluating $\limsup_{T \to \infty}$ along a subsequence where $\frac{N_E^*(T_k)}{T_k} \to \lambda_E^* > 0$:
$$\limsup_{T\to\infty} \frac{A(T)}{T} \ge R + \lambda_E^*(c_{\min} - R \cdot \Delta t_{\text{event}}) > R$$
Thus, sublinear effective reset frequency ($\lim_{T \to \infty} \frac{N_E^*(T)}{T} = 0$) is strictly necessary.

---

## 4. Manuscript Updates and Page Verification

Both camera-ready manuscripts in `submission/nice-teas-asia-2026/` were updated in their LaTeX sources:
- `latex/main.tex` (10-page edition)
- `compact-8-page/latex/main.tex` (8-page edition)

The text explicitly documents:
1. Saturated demand with initial burst exploitation and separation $\ge 0.88\,\text{s}$ (or $\ge 1.93\,\text{s}$ under smooth continuous arrival);
2. Steady-state sustained balance $S(t) < 1.60\,\text{tokens} \ll S_{\max}$ guaranteeing zero sustained overflow.

### Compilation and Page Counts (Tectonic 0.17.0 / pypdf 7.0.1):
- **`submission/nice-teas-asia-2026/main.pdf`**: **Exactly 10 pages**.
- **`submission/nice-teas-asia-2026/compact-8-page/main.pdf`**: **Exactly 8 pages**.

---

## 5. Deliverable Verdict

$$\boxed{\textbf{A. NECESSITY PROOF CLOSED WITH EXPLICIT INITIAL-BURST AND SEPARATION CONDITIONS.}}$$

The mathematical proof is completely closed and consistent across physical code mechanics, workload definitions, and manuscript statements.
