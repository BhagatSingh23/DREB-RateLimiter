# Phase 1 Implementation Plan: Addressing Idle-Credit Farming in DREB

**Author:** Distributed Systems & Algorithm Research Team  
**Date:** 2026-11-11  
**Target:** Rigorous mathematical formulation, independent implementation, and empirical validation of anti-farming mechanisms.

---

## 1. Problem Definition & Mathematical Exposition

### 1.1 The Idle-Credit Farming Mechanism
In DREB (both v1 and v2):
- Capacity parameters: $B_{\max} = \frac{3}{7} C$, $B_{\text{rate}} = \frac{3}{7} R$, $S_{\max} = \frac{9}{7} C$, $S_{\text{rate}} = R$, $\text{idle\_cap} = \frac{6}{7} C$, $\text{idle\_gain} = \frac{2}{3} R$, $\text{decay} = 1.5$.
- Instantaneous maximum burst: $B_{\max} + \frac{\text{idle\_cap}}{\text{decay}} = C$.
- The time required for an empty burst bucket to refill and idle credit to reach its ceiling is:
  $$t_{\text{refill}} = t_{\text{burst\_fill}} + \tau = \frac{B_{\max}}{B_{\text{rate}}} + \frac{\text{idle\_cap}}{\text{idle\_gain}} = \frac{C}{R} + \frac{9C}{7R} = \frac{16C}{7R} \approx 2.2857 \frac{C}{R}$$

### 1.2 The Vulnerability
An adversary operates on a periodic cycle of period $T_{\text{cycle}} = D$:
1. Sits completely idle for duration $D \ge t_{\text{refill}}$.
2. At time $t = D$, submits an instantaneous burst of size $C$.
3. Under DREB v1: All $C$ requests are allowed.
4. Under DREB v2: All $C$ requests are allowed, but $a$ increases by:
   $$\Delta a = \frac{\delta \cdot \delta_a}{C} = \frac{\frac{4}{7}C \cdot 2.0}{C} = \frac{8}{7} \approx 1.143$$
   During the subsequent idle interval $D \ge 2.286\,\text{s}$, the abuse score dissipates linearly:
   $$a(t + D) = \max(0, a(t) - D \cdot \lambda_a) = \max(0, 1.143 - 2.286 \times 1.5) = 0.000$$
5. Consequently, the adversary starts every single burst cycle with $a = 0.000$ and full credit $i = \text{idle\_cap}$, defeating v2's adaptive defense completely.

---

## 2. Investigation Plan & Milestones

### Milestone 1: Quantitative Reproduction of Credit Farming
- Build a standalone, high-precision simulator in Java (`research/credit_farming/CreditFarmingReproduction.java`) that traces:
  1. Admitted requests as a function of idle waiting duration $D \in [0.1\tau, 5.0\tau]$.
  2. Admitted traffic under cyclic farming vs burst size.
  3. Abuse score trajectory across multiple cycles in DREB v2.
  4. Comparison against static dual-bucket and Token Bucket baselines.
- Document exact empirical numbers in `research/credit_farming/REPRODUCTION.md`.

### Milestone 2: Formulation and Mathematical Analysis of Candidate Defenses
We formulate three independent candidate mechanisms:
1. **Candidate 1 (DREB-AC: Adaptive Idle-Credit Ceiling via Consecutive Burst Tracking)**:
   - Tracks a consecutive burst draw counter $k_{\text{draw}}$.
   - Effective credit ceiling diminishes with repeat draws:
     $$\text{idle\_cap}_{\text{eff}} = \frac{\text{idle\_cap}_0}{1 + \beta \cdot k_{\text{draw}}}$$
   - $k_{\text{draw}}$ only decrements when the client demonstrates sustained sub-burst compliant activity.
2. **Candidate 2 (DREB-EXP: Exponential Long-Memory Abuse Score)**:
   - Replaces linear time dissipation ($a - \Delta t \cdot \lambda_a$) with exponential half-life decay:
     $$a(t + \Delta t) = a(t) \cdot 2^{-\Delta t / T_{\text{half}}}$$
   - With $T_{\text{half}} = 15.0\,\text{s}$ to $30.0\,\text{s}$, an adversary waiting 2.3 seconds retains $\approx 90\%$ of its accumulated abuse penalty, causing effective decay $\text{decay}_{\text{eff}} = \text{baseDecay} + \alpha \cdot a$ to escalate across consecutive bursts.
3. **Candidate 3 (DREB-CD: Credit Debt & Recovery Cooldown)**:
   - Spending idle credit generates an equal amount of "credit debt" $d_{\text{credit}}$.
   - In subsequent idle periods, refilled idle credit must first repay $d_{\text{credit}}$ before any usable credit accumulates in the active burst pool.

### Milestone 3: Independent Implementation of Candidate Variants
- Implement candidates as clean, standalone classes in `src/main/java/com/dreb/ratelimit/variants/` (or dedicated research package), strictly leaving original baseline classes (`DREBLimiter.java`, `DREBLimiterV2.java`) untouched.
- Implement matching Redis Lua equivalents for any selected headline candidate.

### Milestone 4: Comprehensive Multi-Behavior Evaluation Suite
Test all 6 required client behaviors:
- **Behavior A**: Legitimate client with occasional idle periods (Poisson, long pauses).
- **Behavior B**: Legitimate client resuming after a single long idle period.
- **Behavior C**: Periodic attacker deliberately farming idle credit ($D \approx t_{\text{refill}}$).
- **Behavior D**: Continuously active attacker ($k = 1.5, 2.0, 4.0$).
- **Behavior E**: Client alternating between long idle periods and large bursts.
- **Behavior F**: Adaptive attacker dynamically searching for the optimal waiting period.

Evaluate:
- Admitted malicious requests.
- Legitimate false rejection rate (FRR).
- Burst acceptance for legitimate clients.
- Long-run admission rate.
- Credit accumulation and recovery time.

### Milestone 5: Verification & Safety Proofs
- Prove Cumulative Bound G1 ($A(T) \le S_{\max} + S_{\text{rate}} T$) holds for each proposed variant.
- Write `jqwik` property-based tests verifying state invariants across 1,000 randomized traces per variant.
- Generate full report in `research/credit_farming/PHASE1_REPORT.md`.
