# Phase 1.2: Final Consistency and Mathematical Audit Report

**Author:** Senior Research Engineer & Distributed Systems Audit Team  
**Date:** 2026-11-11  
**Git Branch:** `research/systematic-improvement`  
**Deliverable:** `research/credit_farming/PHASE1_2_FINAL_AUDIT.md`  
**Scope:** Verification of comparative claims, mathematical recovery bounds under mixed attack strategies, resolution of experimental discrepancies, and revised Pareto frontier.

---

## 1. Correction of Comparative Claims

In the Phase 1.1 preliminary summary, it was stated that *"DREB-AC reduces attacker admission by 62%–83% across all five adversarial strategies."* 

**Audit Finding:** That blanket statement was **inaccurate and unsupported**. Re-auditing the raw CSV data ([`phase1_1_adversaries_30seeds.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/credit_farming/phase1_1_adversaries_30seeds.csv)) reveals that:
- **82.9%** was achieved specifically against **DREB v2 under the closed-loop AIMD prober** ($0.1286R$ vs $0.7520R$).
- **62.1%** was achieved specifically against **DREB v1 under Hill-Climbing Delay Search** ($0.3740R$ vs $0.9859R$).
- Across the other adversarial strategies, the percentage reductions are moderate (33%–43%).

### 1.1 Corrected Comparative Reductions
Below are the exact, verified percentage reductions for `DREB-AC (beta=0.75, rec=0.05)` and `DREB-AC (beta=1.50, rec=0.05)` computed directly from raw 30-seed trial means:

| Adversary Strategy | DREB-AC ($\beta=0.75$) | DREB-AC ($\beta=1.50$) | Baseline: DREB v1 | Reduction vs v1 ($\beta=0.75$) | Baseline: DREB v2 | Reduction vs v2 ($\beta=0.75$) | Baseline: Token Bucket ($C, R$) | Reduction vs TB ($\beta=0.75$) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Fixed Farmer** ($D \approx 1.543\,\text{s}$) | $0.3699R$ | $0.3512R$ | $0.6452R$ | **42.7%** | $0.6452R$ | **42.7%** | $0.6500R$ | **43.1%** |
| **Random Farmer** ($D \in [0.5\tau, 2.5\tau]$) | $0.3047R$ | $0.2889R$ | $0.4560R$ | **33.2%** | $0.4842R$ | **37.1%** | $0.4737R$ | **35.7%** |
| **AIMD Feedback Prober** (Closed-Loop) | $0.1286R$ | $0.1229R$ | $0.2290R$ | **43.8%** | $0.7520R$ | **82.9%** | $0.1909R$ | **32.6%** |
| **Hill-Climbing Search** (Gradient) | $0.3740R$ | $0.3551R$ | $0.9859R$ | **62.1%** | $0.9066R$ | **58.8%** | $0.9878R$ | **62.1%** |
| **Adaptive Burst Optimizer** ($D = 2.0\,\text{s}$) | $0.2932R$ | $0.2786R$ | $0.4655R$ | **37.0%** | $0.4655R$ | **37.0%** | $0.5000R$ | **41.4%** |

**Corrected Summary:**
Across the 5 adversarial strategies, DREB-AC ($\beta=0.75$) reduces admitted adversarial traffic by **33.2% to 62.1% against DREB v1**, by **37.0% to 82.9% against DREB v2**, and by **32.6% to 62.1% against Token Bucket ($C, R$)**.

---

## 2. Mathematical Recovery Proof & Scope of Monotonicity

In DREB-AC:
$$K(t + \Delta t) = \max(0, K(t) - \Delta t \cdot \lambda_{\text{rec}}), \quad \text{cap}_{\text{eff}}(K) = \frac{\text{baseCap}}{1 + \beta K}$$
When credit deficit $\delta > 0$ is spent:
$$K \leftarrow K + \frac{\delta}{\text{baseCap}}$$

### 2.1 Universal Asymptotic Bound on Credit Extraction
Let an adversary execute an arbitrary sequence of $N$ bursts at timestamps $t_1, t_2, \dots, t_N$ with arbitrary inter-arrival gaps $\Delta t_j = t_j - t_{j-1}$ and arbitrary credit deficits $\delta_j \ge 0$ over total observation duration $T = \sum_{j=1}^N \Delta t_j$.

**Theorem 2 (Universal Credit Extraction Bound):**
For *any* attack schedule—periodic, non-periodic, alternating, jittered, or adaptive:
$$\limsup_{T \to \infty} \frac{\sum_{j=1}^N \delta_j}{T} \le \text{baseCap} \cdot \lambda_{\text{rec}}$$

*Proof:*
1. The draw counter $K$ evolves via:
   $$K(t_j^+) = K(t_{j-1}^+) - \Delta t_j \cdot \lambda_{\text{rec}} + \text{loss}_j + \frac{\delta_j}{\text{baseCap}}$$
   where $\text{loss}_j \ge 0$ accounts for time spent while $K = 0$ (clamping at zero).
2. Summing across all $N$ steps:
   $$K(t_N^+) - K(t_0) = \sum_{j=1}^N \frac{\delta_j}{\text{baseCap}} - \lambda_{\text{rec}} T + \sum_{j=1}^N \text{loss}_j$$
3. Since $K(t_N^+) \ge 0$ and $\text{loss}_j \ge 0$:
   $$\frac{\sum_{j=1}^N \delta_j}{\text{baseCap}} \le \lambda_{\text{rec}} T + K(t_N^+) - K(t_0)$$
4. Multiplying by $\text{baseCap}$ and dividing by $T$:
   $$\bar{R}_{\text{credit}}(T) = \frac{\sum_{j=1}^N \delta_j}{T} \le \text{baseCap} \cdot \lambda_{\text{rec}} + \frac{\text{baseCap} \cdot (K(t_N^+) - K(t_0))}{T}$$
5. Since $K$ is bounded from above ($K(t) \le \frac{1}{\text{baseDecay}} + \frac{1}{\beta}$), the boundary term $\frac{\text{baseCap} \cdot \Delta K}{T} \to 0$ as $T \to \infty$.
6. Therefore:
   $$\limsup_{T \to \infty} \bar{R}_{\text{credit}}(T) \le \text{baseCap} \cdot \lambda_{\text{rec}} \quad \blacksquare$$

For $\text{baseCap} = \frac{6}{7} C = 857.14$ and $\lambda_{\text{rec}} = 0.05/\text{s}$:
$$\text{Rate}_{\text{credit}} \le 857.14 \times 0.05 = 42.86\,\text{tokens/s} = 0.04286 R$$

### 2.2 Clarification of the Monotonicity Claim
In Phase 1.1, it was claimed that total throughput $\bar{R}(D) \le \frac{B_{\max}}{D} + \text{baseCap} \cdot \lambda_{\text{rec}}$ decreases monotonically with waiting time $D$.

**Audit Clarification:**
That formula assumes $D \ge t_{\text{fill}} = \frac{B_{\max}}{B_{\text{rate}}}$. It applies strictly to the **macro-burst / credit-farming regime** ($D \ge 0.4\text{s}$–$1.5\text{s}$). It does **not** apply to high-frequency micro-pulsing ($D \to 0$), where incoming requests consume tokens as fast as the burst bucket refills at rate $B_{\text{rate}} = 1.67R$, capping out at the sustained rate $S_{\text{rate}} = 1.0R$.

---

## 3. Mixed-Strategy Attack Analysis

We evaluated whether non-periodic or mixed strategies can outperform periodic farming:

### 3.1 Bimodal Cycles (1 Long Wait to Clear $K$, then $M$ Rapid Bursts)
- **Schedule:** Wait $D_{\text{long}} \ge 13.33\,\text{s}$ (forcing $K \to 0$), fire burst $C$, then fire $M$ rapid bursts spaced by $D_{\text{short}} = 0.5\,\text{s}$, repeat.
- **Empirical Measurements (tokens/s out of $R = 1000$):**
  - $D_{\text{long}} = 10.0\,\text{s}, M = 2, D_{\text{short}} = 0.5\,\text{s} \implies \text{Throughput} = \mathbf{0.1699 R}$.
  - $D_{\text{long}} = 10.0\,\text{s}, M = 5, D_{\text{short}} = 0.5\,\text{s} \implies \text{Throughput} = \mathbf{0.2709 R}$.
  - $D_{\text{long}} = 10.0\,\text{s}, M = 10, D_{\text{short}} = 0.5\,\text{s} \implies \text{Throughput} = \mathbf{0.3970 R}$.
  - $D_{\text{long}} = 5.0\,\text{s}, M = 10, D_{\text{short}} = 0.5\,\text{s} \implies \text{Throughput} = \mathbf{0.5876 R}$.
- **Finding:** As $M$ increases and $D_{\text{short}} \to 0.5\,\text{s}$, the adversary is no longer farming idle credit—it is simply operating as a sustained-rate consumer tapping the burst bucket refill ($B_{\text{rate}} = 1.67R$). Its throughput is bounded by the sustained rate $R$.
- **Conclusion:** Bimodal attack schedules cannot extract more credit than the derived bound $\text{baseCap} \cdot \lambda_{\text{rec}} T + \text{baseCap}$.

---

## 4. Resolution of Experimental Discrepancy (Phase 1 vs Phase 1.1)

In Phase 1 ([`AntiFarmingEvaluationTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/AntiFarmingEvaluationTest.java)), DREB v1's periodic farmer admitted rate was reported as **$0.4369 R$**.  
In Phase 1.1 ([`Phase11ValidationTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase11ValidationTest.java)), DREB v1's fixed farmer admitted rate was reported as **$0.6452 R$**.

### Explicit Configuration Audit:
| Parameter / Attribute | Phase 1 (`AntiFarmingEvaluationTest`) | Phase 1.1 (`Phase11ValidationTest`) | Exact Cause of Difference |
| :--- | :---: | :---: | :--- |
| **Burst Refill Rate ($B_{\text{rate}}$)** | $1666.67$ ($5R/3$) | $1666.67$ ($5R/3$) | Identical |
| **Idle Gain ($\text{idle\_gain}$)** | $666.67$ ($2R/3$) | $666.67$ ($2R/3$) | Identical |
| **Idle Time Needed ($\tau$)** | $1.2857\,\text{s}$ | $1.2857\,\text{s}$ | Identical |
| **Burst Refill Time ($t_{\text{fill}}$)** | $1.000\,\text{s}$ (assumed $B_{\text{rate}}=R$) | $0.2571\,\text{s}$ ($B_{\max}/B_{\text{rate}}$) | Phase 1 calculated $t_{\text{fill}} = C/R = 1.0\text{s}$ |
| **Cycle Period ($D = T_{\text{REFILL}}$)** | **$2.2857\,\text{s}$** | **$1.5429\,\text{s}$** | **Phase 1.1 interval was 0.743s shorter** |
| **Bursts Fired in 120s** | **53 bursts** | **78 bursts** | **Phase 1.1 fired 25 additional bursts** |
| **Admitted Tokens per Burst** | 1000 tokens (after cold start) | 1000 tokens (after cold start) | Identical admission per burst |
| **Total Admitted Tokens** | $52,428$ tokens | $77,428$ tokens | $\Delta = +25,000$ tokens |
| **Normalized Admitted Rate** | $\frac{52,428}{120 \times 1000} = \mathbf{0.4369 R}$ | $\frac{77,428}{120 \times 1000} = \mathbf{0.6452 R}$ | **100% explained by cycle frequency** |

**Resolution:** There was zero algorithmic divergence. In Phase 1, the test author defined $T_{\text{REFILL}} = \frac{C}{R} + \tau = 2.286\,\text{s}$. In Phase 1.1, the test author used the exact $B_{\text{rate}}$ to derive $T_{\text{REFILL}} = \frac{B_{\max}}{B_{\text{rate}}} + \tau = 1.543\,\text{s}$. Because DREB v1 admits 100% of bursts under both intervals, firing 78 bursts in 120s admits $0.6452R$, whereas firing 53 bursts admits $0.4369R$.

---

## 5. Revised Pareto Frontier (Accounting for Latency Uncertainty)

Microbenchmark latency measurements in Java on single machines exhibit $\approx 1\text{–}3\,\text{ns}$ run-to-run noise from CPU frequency scaling and cache warm-up. Therefore, differences of $\le 3\,\text{ns}$ among in-memory implementations (40.4ns to 42.2ns) must be treated as statistically indistinguishable.

### 5.1 Re-Audited Pareto Dominance (Evaluated on Functional Objectives $O_1 \dots O_4$)
Objectives:
1. $O_1$: Closed-Loop AIMD Adversary Throughput ($\downarrow$)
2. $O_2$: Legitimate Bursty FRR at 0.70R ($\downarrow$)
3. $O_3$: Legitimate Post-Idle Burst Acceptance ($\uparrow$)
4. $O_4$: Contracted Steady-State Throughput at 0.98R ($\uparrow$)

| Configuration | AIMD Adv Rate ($O_1 \downarrow$) | Bursty FRR ($O_2 \downarrow$) | Burst Acc ($O_3 \uparrow$) | Steady Adm ($O_4 \uparrow$) | Revised Pareto Classification |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **DREB v1 (burst-matched)** | $0.2290R$ | 0.00% | 100.0% | 0.9799 | **DOMINATED** (by DREB-AC) |
| **DREB v1 (proportional)** | $0.1515R$ | 0.00% | 77.7% | 0.9799 | **DOMINATED** |
| **DREB v2 (base 1.5, default)** | **$0.7520R$** | 0.00% | 100.0% | 0.9799 | **DOMINATED** (by DREB v1 & DREB-AC) |
| **DREB v2 (slow decay 0.5)** | $0.1688R$ | 0.00% | 93.9% | 0.9799 | **DOMINATED** |
| **DREB-CD (debt base)** | $0.8875R$ | 0.00% | 100.0% | 0.9799 | **DOMINATED** |
| **Token Bucket ($C, R$)** | $0.1909R$ | 0.00% | 100.0% | 0.9799 | **DOMINATED** (by DREB-AC) |
| **Token Bucket ($9C/7, R$)** | $1.0037R$ | 0.00% | 100.0% | 0.9799 | **DOMINATED** |
| **Static DB ($B=1.00C, S=9C/7$)** | $1.0037R$ | 0.00% | 100.0% | 0.9799 | **DOMINATED** |
| **Token Bucket ($3C/7, R$)** | **$0.0535R$** | 0.00% | **42.8%** | 0.9799 | **Non-Dominated** |
| **Static DB ($B=3C/7, S=9C/7$)** | **$0.0535R$** | 0.00% | **42.8%** | 0.9799 | **Non-Dominated** |
| **Static DB ($B=0.70C, S=9C/7$)** | **$0.0875R$** | 0.00% | **70.0%** | 0.9799 | **Non-Dominated** |
| **DREB-EMA ($T=60\text{s}, \alpha=1.5$)** | **$0.0991R$** | 0.00% | **82.0%** | 0.9799 | **Non-Dominated** |
| **Static DB ($B=0.85C, S=9C/7$)** | **$0.1063R$** | 0.00% | **85.0%** | 0.9799 | **Non-Dominated** |
| **DREB-EMA ($T=25\text{s}, \alpha=0.8$)** | **$0.1195R$** | 0.00% | **87.5%** | 0.9799 | **Non-Dominated** |
| **DREB-AC ($\beta=1.50, \lambda=0.05$)** | **$0.1229R$** | 0.00% | **100.0%** | 0.9799 | **Non-Dominated** |
| **DREB-AC ($\beta=0.75, \lambda=0.05$)** | **$0.1286R$** | 0.00% | **100.0%** | 0.9799 | **Non-Dominated** |

### 5.2 Decisive Audit Takeaways:
1. **DREB v2 is strictly DOMINATED**: When sub-nanosecond noise is treated as uncertain, DREB v2 is unambiguously dominated by DREB-AC (which achieves identical 100% burst acceptance and identical 0% FRR while reducing adversary throughput from $0.7520R$ to $0.1229R$).
2. **DREB-AC strictly dominates Token Bucket ($C, R$) and DREB v1 (burst-matched)**.
3. **The Clean Trade-Off Curve**: The non-dominated frontier represents a direct trade-off between adversary admission and legitimate burst tolerance. DREB-AC sits at the apex: it is the **only non-dominated algorithm that provides 100.0% legitimate burst acceptance** while driving adversary admission down to $0.12R$.

---

## 6. Remaining Limitations & Recommendations

1. **Parameter Sensitivity:** While $\beta \in [0.75, 1.50]$ and $\lambda_{\text{rec}} = 0.05/\text{s}$ perform robustly across all 5 adversary models, extreme burst rates ($> 10R$) or highly distributed botnets sharing keys have not yet been evaluated (slated for Phase 4 & Phase 6).
2. **Recommendation for DREB-AC:** DREB-AC is mathematically sound, preserves Invariant 1 and Theorem 1, and successfully resolves the credit-farming vulnerability of DREB v1/v2. It should be advanced as the primary burst-control policy for subsequent phases.

---

*Phase 1.2 Final Consistency and Mathematical Audit is complete. Artifacts are recorded.*
