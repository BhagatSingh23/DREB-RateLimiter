# Phase 4 — Formal Guarantee Coverage and Robustness Audit

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`f2b0730`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Artifacts:** Phase 1.3.1 Proof, Phase 2 Formulations, Phase 3 Ablations, Phase 4 Benchmark Data

---

## 1. Formal Taxonomy of Guarantees

The DREB-RCA70 architecture is governed by four primary mathematical guarantees established across earlier project phases:

1. **Guarantee 1 (G1 — Sustained Rate Invariant):**
   Over any measurement interval $\Delta t \ge 0$, the cumulative admitted tokens from any single client identity satisfy:
   $$\text{Admitted}(\Delta t) \le S(t_0) + R \cdot \Delta t$$
   where $S(t) \le S_{\max} = \frac{9}{7}C \approx 1.2857C$.
2. **Guarantee 2 (G2 — Base Burst and Idle Recovery Invariant):**
   A client in initial state $\mathbf{x}_0 = \langle B_0=0, S_0 \ge B_{\max} + \text{baseCap}/\text{baseDecay}, I_0=0, K_0=0 \rangle$ requires an idle duration:
   $$\tau_{\min} = \frac{B_{\max}}{R_{\text{burst}}} + \frac{\text{baseDecay}(C - B_{\max})}{R_{\text{burst}}} = 0.420\,\text{s} + 0.300\,\text{s} = 0.720\,\text{s}$$
   to harvest a single full nominal burst $c = C = 1000$ tokens.
3. **Guarantee 3 (G3 — Post-Attack Recovery and Non-Starvation):**
   When an adversary ceases burst demands at $t = t_{\text{cease}}$, the abuse index $K(t)$ recovers towards $0$ with rate $\lambda_{\text{rec}} = 0.05\,\text{s}^{-1}$:
   $$K(t_{\text{cease}} + \Delta t) = K(t_{\text{cease}}) \cdot e^{-\lambda_{\text{rec}} \Delta t}$$
   Furthermore, compliant bursts $c \le B_{\max} = 700$ require **zero idle credit**, ensuring that compliant clients experience **$0\%$ false rejection** regardless of the lingering abuse penalty $K(t)$.
4. **Theorem 2 (Anti-Farming Extraction Bound):**
   For any periodic or aperiodic sequence of idle-farming intervals $\{D_j\}_{j=1}^M$ where each idle draw depletes the burst bucket to zero, the aggregate credit-funded admission rate is bounded by:
   $$\bar{R}_{\text{credit}} \le \frac{\text{baseCap}}{\bar{D}(1 + \beta K_{\text{ss}})} \to 0 \quad \text{as } K_{\text{ss}} \uparrow$$

---

## 2. Attack-by-Attack Guarantee Classification

Each of the 17 evaluated attacks is formally classified into one of four rigor tiers:
- **Tier 1: Covered by Formal Proof** (provably bounded by G1, G2, G3, or Theorem 2).
- **Tier 2: Covered Under Explicitly Stated Assumptions** (holds provided model assumptions are satisfied).
- **Tier 3: Empirically Evaluated but Not Formally Covered** (no closed-form proof, but verified empirically across 30 seeds).
- **Tier 4: Demonstrated Counterexample** (empirical failure of a theoretical claim).

| Attack Code & Name | Classification Tier | Governing Guarantee | Verification Status | Notes and Scope Restrictions |
| :--- | :--- | :--- | :--- | :--- |
| **A1 Micro-Pulsing** | **Tier 1: Covered by Formal Proof** | Theorem 2 & G1 | **Verified** | Credit rate clamped to $0.0667R$; $K(T)$ escalates to $5.9514$. |
| **A2 Boundary Jitter** | **Tier 1: Covered by Formal Proof** | Theorem 2 & G1 | **Verified** | Jitter $\pm 20\,\text{ms}$ fails to bypass exponential penalty accumulation. |
| **A3 Periodic Full Burst** | **Tier 1: Covered by Formal Proof** | Theorem 2 & G2 | **Verified** | Throughput clamped to $0.0833R$ ($0.0250R$ credit); $K(T) = 0.4867$. |
| **A4 Sinusoidal Wait** | **Tier 2: Covered Under Assumptions** | Theorem 2 | **Verified** | Assumes single-client state tracking across harmonic variations. |
| **B1 White-Box Optimal** | **Tier 1: Covered by Formal Proof** | Guarantee G1 | **Verified** | Sustained rate clamped to $0.9837R \le 1.0000R$; credit bounded to $0.0136R$. |
| **B2 Black-Box Inferred**| **Tier 1: Covered by Formal Proof** | Theorem 2 & G1 | **Verified** | Adaptive search forces $K(T) = 2.8939$; credit rate restricted to $0.0433R$. |
| **C1 AIMD Adaptation** | **Tier 1: Covered by Formal Proof** | Theorem 2 & G1 | **Verified** | AIMD oscillations drive $K(T) = 2.8528$; total admitted rate held to $0.3467R$. |
| **C2 Binary Search Cost**| **Tier 1: Covered by Formal Proof** | Theorem 2 & G1 | **Verified** | Binary convergence restricted to $0.0349R$ credit; total rate $0.6370R$. |
| **C3 Credit Maximizer** | **Tier 1: Covered by Formal Proof** | Theorem 2 & G2 | **Verified** | Identical trajectory to A3; credit rate bounded at $0.0250R$. |
| **D1 Competing Poisson** | **Tier 2: Covered Under Assumptions** | G1 & G3 | **Verified** | Assumes background load does not exceed $R - \bar{r}_{\text{adv}}$. FRR = $0.22\%$. |
| **D2 Multi-Worker Col.** | **Tier 3: Empirically Evaluated** | N/A (State Collision) | **Verified** | Admitted rate $1.0039R \approx 1.0R$; no underflow or state corruption. |
| **D3 Post-Attack Recov.**| **Tier 1: Covered by Formal Proof** | Guarantee G3 | **Verified** | **$0.00\%$ FRR** for compliant bursts; clean separation of $B_{\max}$ and credit. |
| **E1 Near-$B_{\max}$** | **Tier 1: Covered by Formal Proof** | G1 & G2 | **Verified** | Costs $\le 700$ consume $0$ credit; $c=701$ consumes exactly $1 \times 1.5$ credit. |
| **E2 Near-$C$ Capacity** | **Tier 1: Covered by Formal Proof** | Theorem 2 & G2 | **Verified** | Clamped to $0.0833R$ identical to A3; $c > 1000$ strictly denied. |
| **E3 Micro-Cost Flood** | **Tier 1: Covered by Formal Proof** | Guarantee G1 | **Verified** | High-frequency $c=1$ strictly bounded to $1.0214R \approx R$; $0.0000$ credit. |
| **E4 Overdraw ($c=1500$)**| **Tier 1: Covered by Formal Proof** | Guarantee G1 ($S_{\max}$) | **Verified** | Strictly $0.00\%$ admitted across all seeds; zero state degradation. |
| **E5 Bimodal Toggle** | **Tier 1: Covered by Formal Proof** | Theorem 2 & G1 | **Verified** | Total rate held to $0.0843R$; credit clamped to $0.0250R$. |

---

## 3. Deep-Dive Audit of Specific Guarantees

### 3.1 Theorem 2 Invariant Audit (Credit-Accrual Episodes)
- **Question:** Does the attacker ever harvest idle credit without first depleting the burst bucket $B(t)$ to zero?
- **Finding:** Under all 17 attacks, requests drawing idle credit satisfy $c > B(t)$, causing $B(t)$ to be debited to zero immediately.
- **Result:** The credit-accrual episode structure proven in Phase 1.3.1 strictly holds in production execution. No sub-interval credit leak was observed.

### 3.2 Guarantee G3 vs DREB-AC BM Collateral Damage
- **Question:** Why does DREB-AC BM fail Guarantee G3 while DREB-RCA70 succeeds?
- **Finding:**
  - In DREB-AC BM, $\alpha = 0.30 \implies B_{\max} = 428.57$. A legitimate client requesting standard burst $c = 700$ must draw $700 - 428.57 = 271.43$ tokens from idle credit.
  - If the limiter key previously experienced adversarial farming, $K(t)$ is elevated ($K \approx 1.7010$), which drastically discounts effective credit:
    $$I_{\text{eff}} = \frac{I(t)}{1 + 0.75 \times 1.7010} = \frac{I(t)}{2.2758}$$
  - Because $I_{\text{eff}} / 1.5 < 271.43$, compliant legitimate requests are rejected ($\text{FRR} = 60.00\%$).
  - In **DREB-RCA70**, $\alpha = 0.70 \implies B_{\max} = 700$. The compliant request is satisfied entirely by $B(t)$, requiring **zero idle credit**. The abuse index $K(t)$ is never checked for requests with $c \le B_{\max}$.
- **Result:** RCA70 decouples base burst compliance from idle credit penalties, establishing structural immunity against post-attack collateral starvation.

### 3.3 Multi-Worker Concurrency and State Collisions (Attack D2)
- **Question:** Can uncoordinated multi-worker burst collisions corrupt the limiter's state or bypass sustained limits?
- **Finding:**
  - 4 parallel workers generated unsynchronized bursts of $c=500$ at $1\,\text{s}$ intervals, offering a combined rate of $2.0R$.
  - RCA70 admitted an aggregate rate of $1.0039R \pm 0.0078R$, exactly matching the sustained contract rate.
  - Credit consumption was negligible ($0.0144R$), and the abuse index stabilized at $K(T) = 0.6406$.
  - In Redis/distributed deployments, atomic Lua scripts guarantee serializability; in Java in-memory deployments, synchronization prevents race corruption.
- **Classification:** Categorized as **Tier 3 (Empirically Evaluated)** because the interaction of non-deterministic arrival orderings is verified empirically rather than closed in an analytical proof.

---

## 4. Residual Theoretical Gaps and Non-Claims

1. **Finite Empirical Testing vs Formal Safety:**
   Passing 2,550 experimental runs across 17 attack generators demonstrates that RCA70 is robust against known classes of adaptive, state-aware, and boundary attacks. It does **not** constitute a formal proof of security against all conceivable adversarial strategies outside the modeled state-space.
2. **Distributed Sybil and Identity-Spoofing Attacks:**
   All guarantees assume that request streams can be reliably mapped to a client identifier (e.g., authenticated API key or IP subnet). If an attacker distributes requests across $M$ distinct Sybil identities, each limiter instance operates independently, scaling aggregate throughput by $M \times R$. Addressing distributed Sybil attacks requires global network-layer rate allocation, which is orthogonal to single-node limiter design.
3. **Black-Box Asymptotic Convergence Bounds:**
   While the black-box attacker (B2) and AIMD controller (C1) were observed to achieve lower throughput than white-box optimal adversaries, we do not prove an information-theoretic lower bound on the rate of convergence for black-box boundary estimators.
