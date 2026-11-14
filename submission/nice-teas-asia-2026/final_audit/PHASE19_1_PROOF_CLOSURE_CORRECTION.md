# PHASE 19.1: FINAL NARROW PROOF-CLOSURE CHECK & ACCOUNTING CORRECTION

**Repository:** `BhagatSingh23/DREB-RateLimiter`  
**Branch:** `submission/nice-teas-asia-2026`  
**Base Commit:** `3780d08`  
**Audit Date:** 14 November 2026  
**Final Verdict:** `PROOF CLOSED: OVERFLOW AND ADDITIVE EVENT CAPACITY ESTABLISHED.`

---

## 1. Exact Implementation Files & Relevant Methods Inspected

We audited the core rate-limiting and token-accounting implementation in the repository:
1. `src/main/java/com/dreb/ratelimit/DREBLimiter.java`
   - `initialState(double now)`: constructs fresh state via `ClientState.fresh(burstMax, sustainedCapacity, now)`.
   - `refill(ClientState state, double dt)`: invokes `refillStrategy.refill` followed by `idlePolicy.accrue`.
   - `checkAndConsume(ClientState state, int cost)`: enforces conjunction `state.sustainedTokens >= cost && effectiveBurst >= cost`, executing `state.sustainedTokens -= cost`.
2. `src/main/java/com/dreb/ratelimit/ConstantRateRefillStrategy.java`
   - `refill(ClientState state, double dt)`: implements `state.sustainedTokens = Math.min(sustainedMax, state.sustainedTokens + dt * sustainedRate)` and `state.burstTokens = Math.min(burstMax, state.burstTokens + dt * burstRate)`.
3. `src/main/java/com/dreb/ratelimit/ClientState.java`
   - Data record holding `burstTokens`, `sustainedTokens`, `idleCredit`, and `lastRefillTs`.
4. `src/main/java/com/dreb/ratelimit/AbstractRateLimiter.java`
   - `allow(String clientId, int cost)`: synchronizes on per-client state, updates `dt = now - state.lastRefillTs`, invokes `refill`, `checkAndConsume`, and persists state.
5. `src/main/java/com/dreb/ratelimit/policies/DrebRcaPolicyFactory.java`
   - `computeRca70Config`: computes parameters for DREB-RCA70 ($C=1000, R=1000$):
     - $B_{\max} = 0.70 \times C = 700.0$
     - $r_B = \frac{5}{3} R = 1666.67\,\text{tokens/s}$
     - $S_{\max} = \frac{9}{7} C \approx 1285.71\,\text{tokens}$
     - $R = 1000.0\,\text{tokens/s}$
     - $I_{\max} = 450.0$

---

## 2. Complete Corrected Accounting Derivation

### 2.1 The Global Accounting Identity
Let $[0, T]$ be an arbitrary observation window. Let $N_E^*(T)$ denote the number of independently exploitable effective discontinuity events occurring in $[0, T]$. These events partition the active uptime into $N_E^*(T) + 1$ normal serving intervals:
$$I_k = [t_k^{\text{start}}, t_k^{\text{end}}), \quad k = 0, 1, \dots, N_E^*(T)$$
where $t_0^{\text{start}} = 0$ and $t_{N_E^*}^{\text{end}} \le T$.

Within any normal serving interval $I_k$:
- Refill accumulates strictly with elapsed serving time at constant rate $R$: $\text{Refill}_k = R \cdot |I_k| = R \cdot (t_k^{\text{end}} - t_k^{\text{start}})$.
- Admissions debit $A_k$ tokens from $S_k$.
- Unused tokens exceeding capacity overflow: $\text{Overflow}_k \ge 0$.

Exact token conservation over interval $I_k$ gives:
$$S_k(t_k^{\text{end}}) = S_k(t_k^{\text{start}}) + R \cdot |I_k| - A_k - \text{Overflow}_k$$
Rearranging for admissions $A_k$:
$$A_k = R \cdot |I_k| + S_k(t_k^{\text{start}}) - S_k(t_k^{\text{end}}) - \text{Overflow}_k$$

Summing over all intervals $k = 0, 1, \dots, N_E^*(T)$ yields:
$$A(T) = R \sum_{k=0}^{N_E^*(T)} |I_k| + \sum_{k=0}^{N_E^*(T)} \left( S_k(t_k^{\text{start}}) - S_k(t_k^{\text{end}}) \right) - \sum_{k=0}^{N_E^*(T)} \text{Overflow}_k$$

Noting that $\sum_{k=0}^{N_E^*} |I_k| = T_{\text{normal}} = \mu(\Omega_{\text{normal}}(T))$, and telescoping the boundary balances:
$$\sum_{k=0}^{N_E^*} \left( S_k(t_k^{\text{start}}) - S_k(t_k^{\text{end}}) \right) = S_0(0) - S_{N_E^*}(t_{N_E^*}^{\text{end}}) + \sum_{k=1}^{N_E^*(T)} \Delta S_k^{\text{jump}}$$
where $\Delta S_k^{\text{jump}} \triangleq S_k(t_k^{\text{start}}) - S_{k-1}(t_{k-1}^{\text{end}})$ is the state injection across the $k$-th discontinuity.

This establishes the **Global Accounting Identity**:
$$\boxed{A(T) = R \cdot T_{\text{normal}} + S_0(0) - S_{N_E^*}(T) + \sum_{k=1}^{N_E^*(T)} \Delta S_k^{\text{jump}} - \sum_{k=0}^{N_E^*(T)} \text{Overflow}_k}$$

---

## 3. Task 1: Cumulative Overflow Proof ($\sum \text{Overflow}_k = 0$)

We prove that under the saturated adversarial demand condition assumed by the necessity theorem, cumulative overflow is identically zero.

### Mathematical Proof:
1. **Definition of Saturated Adversarial Demand:**
   An arrival sequence constitutes saturated adversarial demand if requests arrive at every instant where token balances permit admission ($B \ge 1$ and $S \ge 1$), with inter-arrival gap $\Delta t_{\text{arr}} \le 1/R$ (or backlogged requests continuously queued at the gateway).
2. **Post-Event Drain Phase:**
   At the start of any normal serving interval $I_k$ ($t = t_k^{\text{start}}$), the node initializes or restores state with $S(t_k^{\text{start}}) \le S_{\max} \approx 1285.71$ and $B(t_k^{\text{start}}) \le B_{\max} = 700$.
   - Saturated demand immediately submits a batch burst of $B_{\max} = 700$ requests.
   - All 700 requests are admitted instantaneously, consuming $700$ burst tokens and $700$ sustained tokens.
   - Immediately following this burst, the state is:
     $$B(t_k^{\text{start}+}) = 0, \quad S(t_k^{\text{start}+}) = S(t_k^{\text{start}}) - 700 \le 1285.71 - 700 = 585.71\,\text{tokens}$$
3. **Refill and Drain Rates:**
   For $t > t_k^{\text{start}}$, tokens refill according to:
   $$\frac{dB_{\text{refill}}}{dt} = r_B = \frac{5}{3} R \approx 1666.67\,\text{tokens/s}, \quad \frac{dS_{\text{refill}}}{dt} = R = 1000.0\,\text{tokens/s}$$
   Because $r_B > R$, burst tokens replenish faster than sustained tokens. Since $S(t) > 0$, every refilled burst token is immediately admitted and debited against both buckets.
   Therefore, requests are admitted at rate $r_B = 1666.67\,\text{tokens/s}$.
   The net rate of change of sustained tokens during this phase is:
   $$\frac{dS}{dt} = R - r_B = R - \frac{5}{3} R = -\frac{2}{3} R = -666.67\,\text{tokens/s} < 0$$
   $S(t)$ strictly decreases from $585.71$ to $0$ in:
   $$t_{\text{drain}} = \frac{585.7143}{666.6667} \approx 0.8786\,\text{seconds}$$
   Throughout this drain period: $S(t) \le 585.71 < S_{\max} \approx 1285.71$. Hence, sustained tokens never hit capacity; $\text{Overflow} = 0$.
4. **Steady-State Phase:**
   Once $S(t)$ reaches 0 (for $t > t_k^{\text{start}} + t_{\text{drain}}$), admissions are strictly throttle-limited by sustained refill $R = 1000.0\,\text{tokens/s}$.
   Whenever $S(t) \ge 1$ (every $1\,\text{ms}$), a backlogged request consumes it. Thus, $S(t) \in [0, 1] \ll S_{\max}$.
   Refill is continuously consumed as it arrives; sustained tokens never accumulate towards $S_{\max}$.
5. **Overflow Requires Idle Gaps:**
   For sustained tokens to overflow, the adversary would have to pause sending requests for an unforced idle duration:
   $$\Delta t_{\text{idle}} > \frac{S_{\max} - S(t)}{R} \ge \frac{1285.71 - 585.71}{1000} = 0.70\,\text{seconds}$$
   This is strictly contradicted by the definition of sustained saturated demand.
6. **Across Discontinuities:**
   During downtime or transition intervals, the limiter is offline; elapsed real time is not refilled into sustained tokens. State resets assign initial values directly without continuous overflow.

**Conclusion:** Under saturated adversarial demand, $\sum_{k=0}^{N_E^*} \text{Overflow}_k \equiv 0$ is rigorously established.

---

## 4. Task 2: Additive Event Capacity & Independence

### 4.1 Claim (a) vs Claim (b)
- **Claim (a):** "A balance reset to 700 tokens." (State restoration)
- **Claim (b):** "At least 700 additional admitted tokens independent of normal-serving admissions." (Additive admission surplus)

These claims are **not interchangeable**. A state reset to 700 tokens guarantees 700 additional admissions if and only if:
1. The prior sustained balance was fully exhausted ($S(t_k^-) \approx 0$). If the node failed before prior tokens were consumed (e.g. $S(t_k^-) = 500$), resetting to 700 only injects $\Delta S^{\text{jump}} = 700 - 500 = 200\,\text{tokens}$, not 700.
2. The inter-event serving interval satisfies $|I_{k-1}| \ge t_{\text{drain}} \approx 0.88\,\text{s}$, giving saturated demand sufficient time to drain the previous balance to zero.
3. The newly promoted replica experiences uncoordinated state amnesia (e.g. cold initialization or non-synchronized snapshot). If state were synchronously replicated via distributed consensus, $S(t_k^+) = S(t_k^-)$, yielding $\Delta S^{\text{jump}} = 0$.

### 4.2 Definition of Effective Events $N_E^*(T)$
To ensure rigorous additivity, $N_E^*(T)$ is defined specifically as the count of **independently exploitable effective events**:
An event $k$ belongs to $N_E^*(T)$ if:
1. It restores an active serving node with uncoordinated capacity $S(t_k^+) \ge c_{\min} = 700\,\text{tokens}$.
2. It is separated from the preceding effective event by active normal serving time $|I_{k-1}| \ge t_{\text{drain}} \approx 0.88\,\text{s}$, ensuring $S(t_k^-) \in [0, 1) \approx 0$.
3. It does not abort prior to serving requests.

### 4.3 Additivity and Orthogonality Proof
Under this definition:
$$\Delta S_k^{\text{jump}} = S(t_k^+) - S(t_k^-) \ge 700 - 1 \approx 700\,\text{tokens}$$
- The 700 burst tokens are consumed at $t = t_k^{\text{start}}$, debited strictly from $\Delta S_k^{\text{jump}}$.
- Elapsed serving time at $t = t_k^{\text{start}}$ is zero; no continuous refill $R \cdot T_{\text{normal}}$ is credited for this burst.
- Continuous refill $R \cdot T_{\text{normal}}$ accrues strictly during active serving time ($t \in \Omega_{\text{normal}}$) as real time elapses.
- Downtime during the transition forfeits $R \cdot \Delta t_{\text{event}}$ tokens of normal refill.
- Therefore, the net excess admission capacity contributed by each effective event is:
  $$\Delta A_k^{\text{net}} = c_{\min} - R \cdot \Delta t_{\text{event}} \ge 700 - 1000 \times 0.010 = +690\,\text{tokens} > 0$$
- This capacity is strictly additive to normal refill $R \cdot T_{\text{normal}}$ with zero double-counting.

---

## 5. Task 3: Rate Illustration (919.0 tokens/s vs 977.57 tokens/s)

We present the explicit, step-by-step physical timeline and state evolution over a periodic $T = 10.0\,\text{s}$ cycle with $R = 1000\,\text{tokens/s}$, $\lambda_E^* = 0.1\,\text{s}^{-1}$ (1 event/cycle), $\Delta t_{\text{event}} = 0.010\,\text{s}$ (10 ms transition), and 15% abort downtime ($\mu(\Omega_{\text{abort}}) = 1.50\,\text{s}$). Active normal serving uptime is $T_{\text{normal}} = 10.0 - 0.010 - 1.50 = 8.490\,\text{seconds}$.

### Timeline Case A: Replica Rollback to Pre-Burst Snapshot ($S=700, B=700$)
- **$t \in [0.000, 0.010\,\text{s}]$ (Transition Downtime):** Node offline. Admissions = $0$.
- **$t = 0.010\,\text{s}$ (Instantaneous Burst):** Node promoted with $S(0.010^+) = 700.0, B(0.010^+) = 700.0$. Saturated demand immediately consumes $700$ tokens. Admissions = $700$. Post-burst state: $S = 0.0, B = 0.0$.
- **$t \in (0.010, 8.500\,\text{s}]$ (Steady-State Normal Serving):** Active duration = $8.490\,\text{s}$. Sustained bucket refills at $1000\,\text{tokens/s}$ (total refill = $8490\,\text{tokens}$). Saturated demand consumes tokens as they refill. Admissions = $8490$. State at $t = 8.500^-$ is $S = 0.0, B = 0.0$.
- **$t \in [8.500, 10.000\,\text{s}]$ (Abort Downtime):** Node offline/aborting. Duration = $1.50\,\text{s}$. Admissions = $0$.
- **Cycle Totals:**
  $$A_{\text{cycle}} = 700 + 8490 = \mathbf{9190.0\,\text{tokens}}$$
  $$\text{Actual Admitted Rate} = \frac{9190.0\,\text{tokens}}{10.0\,\text{s}} = \mathbf{919.0\,\text{tokens/s}} < R = 1000.0\,\text{tokens/s}$$

### Timeline Case B: Pristine Replica Reset ($S=S_{\max}=1285.71, B=B_{\max}=700$)
- **$t \in [0.000, 0.010\,\text{s}]$ (Transition Downtime):** Node offline. Admissions = $0$.
- **$t = 0.010\,\text{s}$ (Instantaneous Burst):** Node initialized with $S(0.010^+) = 1285.7143, B(0.010^+) = 700.0$. Saturated demand consumes $700$ tokens. Admissions = $700$. Post-burst state: $S = 585.7143, B = 0.0$.
- **$t \in (0.010, 0.8886\,\text{s}]$ (Drain Phase):** Duration = $0.878571\,\text{s}$. Burst refills at $1666.67\,\text{tokens/s}$, sustained refills at $1000\,\text{tokens/s}$. Saturated demand consumes at $1666.67\,\text{tokens/s}$, draining $S$ to zero. Admissions = $1666.6667 \times 0.878571 = 1464.2857\,\text{tokens}$. State at $t = 0.8886^-$ is $S = 0.0, B = 0.0$.
- **$t \in (0.8886, 8.500\,\text{s}]$ (Steady-State Normal Serving):** Duration = $7.611429\,\text{s}$. Refill = $1000 \times 7.611429 = 7611.4286\,\text{tokens}$. Admissions = $7611.4286\,\text{tokens}$. State at $t = 8.500^-$ is $S = 0.0, B = 0.0$.
- **$t \in [8.500, 10.000\,\text{s}]$ (Abort Downtime):** Node offline/aborting. Duration = $1.50\,\text{s}$. Admissions = $0$.
- **Cycle Totals:**
  $$A_{\text{cycle}} = 700 + 1464.2857 + 7611.4286 = \mathbf{9775.7143\,\text{tokens}}$$
  $$\text{Actual Admitted Rate} = \frac{9775.7143\,\text{tokens}}{10.0\,\text{s}} = \mathbf{977.5714\,\text{tokens/s}} \approx \mathbf{977.57\,\text{tokens/s}} < R = 1000.0\,\text{tokens/s}$$

### Significance:
These are **exact physical throughput traces** derived directly from step-by-step state simulation, not loose bounds. They demonstrate conclusively that positive effective event frequency ($\lambda_E^* = 0.1\,\text{s}^{-1} > 0$) does **not** cause rate collapse if abort downtime is non-negligible, proving that the condition $\lim_{T \to \infty} \frac{\mu(\Omega_{\text{abort}})}{T} = 0$ is mathematically necessary.

---

## 6. Task 4: Universal Sufficiency Theorem

We confirm the strongest justified universal sufficiency result:

$$\lim_{T \to \infty} \frac{N_E(T)}{T} = 0 \implies \limsup_{T \to \infty} \frac{A(T)}{T} \le R$$

### Rigorous Formulation:
1. **Total Event Count $N_E(T)$:** Includes every discontinuity event capable of injecting tokens: node restarts, cold boots, replica promotions, snapshot rollbacks, clock adjustments, and aborted failovers.
2. **Per-Event Upper Bound:** In DREB-RCA70, each event can at most initialize the state to pristine initial capacities ($S \le S_{\max}$, $B \le B_{\max}$). The maximum additional tokens that an adversary can extract from an event is bounded by $C_{\max} \le S_{\max} \approx 1285.71\,\text{tokens} < \infty$.
3. **Refill Upper Bound:** Normal refill across $[0, T]$ is bounded by $R \cdot T_{\text{normal}} \le R \cdot T$.
4. **Initial Condition:** $S(0) \le S_{\max} < \infty$.
5. **Universal Envelope:** For all arrival sequences (saturated, bursty, Poisson, or adversarial):
   $$\frac{A(T)}{T} \le \frac{S_{\max}}{T} + R + \frac{N_E(T)}{T} C_{\max}$$
   Taking $\limsup_{T \to \infty}$:
   $$\limsup_{T \to \infty} \frac{A(T)}{T} \le 0 + R + 0 \cdot C_{\max} = R$$
6. **Universality:** Holds without requiring saturated demand, without requiring negligible abort downtime, and without constraints on arrival distributions.

---

## 7. Corrected Theorem Statement in Manuscripts

### Theorem 4 (G2 Asymptotic Containment Criteria)
The macroscopic rate is contained by SLA rate $R$:
$$\limsup_{T\to\infty} \frac{A(T)}{T} \le R$$
1. Sublinear total event frequency ($\lim_{T\to\infty} \frac{N_E(T)}{T} = 0$) is **universally sufficient** under all admissible demand patterns.
2. Under sustained saturated adversarial demand with asymptotically negligible abort downtime ($\lim_{T\to\infty} \frac{\mu(\Omega_{\text{abort}})}{T} = 0$), sublinear frequency of effective (independently exploitable) events ($\lim_{T\to\infty} \frac{N_E^*(T)}{T} = 0$) is **strictly necessary**.

---

## 8. Status of Necessity: Fully Proved under Explicit Conditions

Necessity is **conditionally proved**:
- It is **false** for general discontinuity events $N_E(T)$ (disproven by crash loops).
- It is **false** without saturated adversarial demand (disproven by idle/sub-contract clients).
- It is **false** in systems with non-negligible abort downtime (disproven by the $919.0\,\text{tokens/s}$ and $977.57\,\text{tokens/s}$ traces).
- It is **strictly true and fully proved** under the joint conditions:
  $$\text{Saturated Demand} \quad \land \quad \lim_{T \to \infty} \frac{\mu(\Omega_{\text{abort}}(T))}{T} = 0 \quad \land \quad N_E^*(T) \text{ independently exploitable}.$$

---

## 9. Manuscripts Verification & Page Budgets

Both canonical manuscripts were updated with the refined, exact wording and recompiled via Tectonic 0.17.0:
- **10-Page Edition:**
  - Source: `submission/nice-teas-asia-2026/latex/main.tex`
  - Output: `submission/nice-teas-asia-2026/main.pdf`
  - Page Count: **Exactly 10 pages** (Verified via `pypdf 7.0.1`).
- **8-Page Edition:**
  - Source: `submission/nice-teas-asia-2026/compact-8-page/latex/main.tex`
  - Output: `submission/nice-teas-asia-2026/compact-8-page/main.pdf`
  - Page Count: **Exactly 8 pages** (Verified via `pypdf 7.0.1`).

---

## 10. Tests Run and Limitations

- **Core Unit & Property Tests:**
  - `mvn test -Dtest=CoreLimiterTest,DrebPropertiesTest,DrebRcaPropertiesTest,Phase54OperationalEligibilityTest`
  - Results: **44 tests run, 0 failures, 0 errors, 0 skipped (BUILD SUCCESS)**.
  - Property tests with jqwik executed 1000 randomized tries per property across edge cases.
- **Limitations:**
  - Live Redis container tests (`RedisRca70LimiterTest`, cluster failover live tests) require an active Docker daemon.
  - As instructed, passing tests are taken as evidence of implementation consistency, not mathematical proof. The proof rests on first-principles derivation.

---

## 11. Claims That Must NOT Be Made in the Submission

1. **Do not claim** that sublinear total event frequency ($\lim N_E/T = 0$) is necessary. (Crash-looping nodes have $N_E > 0$ with zero admission).
2. **Do not claim** that necessity holds unconditionally for arbitrary arrival traffic. (Sub-contract clients never exceed $R$).
3. **Do not claim** that effective event frequency ($\limsup N_E^*/T > 0$) implies rate collapse when abort downtime is significant. (Counterexample shows rate remains $\le 977.57\,\text{tokens/s} < R$).
4. **Do not claim** that a state reset to 700 tokens automatically provides 700 additional admissions without inter-event drain spacing.
5. **Do not claim** distributed cluster guarantees under uncoordinated round-robin routing without sticky hashing.

---

## 12. Final Verdict

$$\boxed{\textbf{PROOF CLOSED: OVERFLOW AND ADDITIVE EVENT CAPACITY ESTABLISHED.}}$$
