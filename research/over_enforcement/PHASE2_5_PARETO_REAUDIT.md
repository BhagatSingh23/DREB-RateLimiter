# Phase 2.5: Independent Pareto Re-Audit — RCA60 vs RCA70 vs RCA80

## 1. Executive Summary

This report performs a rigorous, independent re-audit of the multi-objective Pareto trade-off between the Rebalanced Capacity Allocation candidates: **DREB-RCA60**, **DREB-RCA70**, and **DREB-RCA80**, reconstructed directly from the raw simulation dataset ([`phase2_experiments_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/over_enforcement/phase2_experiments_raw.csv)).

### Key Findings of the Re-Audit:
1. **RCA80 does NOT Pareto-dominate RCA70:**
   - While RCA80 achieves marginally lower compliant bursty FRR ($14.57\%$ vs $15.63\%$) and lower credit-funded adversarial admission ($0.0398R$ vs $0.0598R$), **RCA80 admits substantially MORE total adversarial traffic ($0.5464R$ vs $0.5031R$, an 8.6% increase in backend attack load)**.
   - RCA70 strictly outperforms RCA80 on **Total Adversarial Admission Rate** ($\bar{A}_{\text{adv}}/R$) because its smaller base bucket ($B_{\max} = 700$ vs $800$) limits the baseline tokens the attacker can harvest without idle credit.
2. **Idle-Conditioned Incentive Preservation:**
   - RCA70 retains an idle burst headroom of **$300$ tokens** ($30\%$ of nominal capacity $C=1000$), preserving the architectural premise of DREB (Invariant G2).
   - RCA80 contracts this headroom to **$200$ tokens** ($20\%$), degenerating towards a standard static Token Bucket.
3. **Non-Dominated Pareto Frontier:**
   - Both **DREB-RCA70** and **DREB-RCA80** belong to the formal non-dominated Pareto frontier.
   - Original DREB baselines (**DREB v1 BM**, **DREB v2 Default**, and **DREB-AC BM**) are strictly dominated.

---

## 2. Reconstructed Multi-Objective Vectors

All metrics are computed across 10 independent random seeds per configuration. Uncertainties represent 95% Student's $t$ / normal confidence intervals ($CI_{95} = \pm 1.96 \cdot \frac{\sigma}{\sqrt{N}}$).

| Metric / Objective Dimension | Unit / Goal | DREB-RCA60 | **DREB-RCA70** | DREB-RCA80 |
| :--- | :---: | :---: | :---: | :---: |
| **Base Burst Capacity ($B_{\max}$)** | tokens | 600 ($0.60C$) | **700 ($0.70C$)** | 800 ($0.80C$) |
| **Idle Headroom ($\Delta C_{\text{idle}} = C - B_{\max}$)** | tokens $\uparrow$ | 400 ($40\%$) | **300 ($30\%$)** | 200 ($20\%$) |
| **Compliant Bursty FRR ($\lambda = 0.783R$)** | % $\downarrow$ | $17.56 \pm 2.61\%$ | **$15.63 \pm 2.66\%$** | $14.57 \pm 2.69\%$ |
| **Overload Admitted Rate ($\lambda = 1.50R$)** | $R$ ($\to 1.0$) | $1.0025 \pm 0.0000$ | **$1.0031 \pm 0.0000$** | $1.0031 \pm 0.0000$ |
| **Overload Deficit ($|1.0 - \bar{A}/R|$)** | $R$ $\downarrow$ | $0.0025R$ ($0.25\%$) | **$0.0031R$ ($0.31\%$)** | $0.0031R$ ($0.31\%$) |
| **Overload Admitted Rate ($\lambda = 1.25R$)** | $R$ ($\to 1.0$) | $0.8951 \pm 0.0000$ | **$0.9903 \pm 0.0000$** | $0.9968 \pm 0.0000$ |
| **Total Adversarial Admission Rate** | $R$ $\downarrow$ | **$0.4597 \pm 0.0000$** | **$0.5031 \pm 0.0000$** | $0.5464 \pm 0.0000$ |
| **Credit-Funded Adversarial Rate** | $R$ $\downarrow$ | $0.0798 \pm 0.0000$ | **$0.0598 \pm 0.0000$** | $0.0398 \pm 0.0000$ |
| **Post-Idle Burst Acceptance ($C=1000$)** | % | 100.0% | **100.0%** | 100.0% |
| **Maximum Post-Idle Burst ($B_{\max} + I_{\text{eff}}$)** | tokens | 1,000 | **1,000** | 1,000 |
| **Decision Latency (Phase 1 Benchmark)** | ns/op $\downarrow$ | $25.4\text{ ns}$ | **$25.6\text{ ns}$** | $25.4\text{ ns}$ |

---

## 3. Pareto Dominance Analysis

### 3.1 Formal Objective Space
Let the objective vector for rate limiter policy $p$ be defined across the four primary performance and security dimensions:
$$\vec{F}(p) = \Big( \text{FRR}_{\text{bursty}}(p), \; \text{Dev}_{\text{overload}}(p), \; \bar{A}_{\text{adv\_total}}(p), \; \bar{C}_{\text{credit\_farmed}}(p) \Big)$$
where each dimension is to be minimized.

A policy $p_A$ strictly Pareto-dominates $p_B$ ($p_A \succ p_B$) if and only if:
$$\forall i \in \{1, 2, 3, 4\}, \; F_i(p_A) \le F_i(p_B) \quad \text{and} \quad \exists j \in \{1, 2, 3, 4\}, \; F_j(p_A) < F_j(p_B)$$

### 3.2 Evaluation of RCA80 vs RCA70
Comparing the objective vectors:
- $\vec{F}(\text{RCA70}) = \left( 15.63\%, \; 0.0031R, \; \mathbf{0.5031R}, \; 0.0598R \right)$
- $\vec{F}(\text{RCA80}) = \left( \mathbf{14.57\%}, \; 0.0031R, \; 0.5464R, \; \mathbf{0.0398R} \right)$

#### Proof that RCA80 does NOT dominate RCA70:
$$F_3(\text{RCA70}) = 0.5031R < F_3(\text{RCA80}) = 0.5464R$$
Because RCA70 is strictly superior to RCA80 on Total Adversarial Admission Rate ($F_3$), RCA80 **cannot** dominate RCA70.

#### The Trade-Off Quantified:
- **What RCA80 gains:** A $1.06\%$ reduction in compliant bursty FRR ($14.57\%$ vs $15.63\%$) and a $0.0200R$ reduction in credit-funded throughput ($0.0398R$ vs $0.0598R$).
- **What RCA80 sacrifices:**
  1. **Total Attack Penetration:** RCA80 admits $0.5464R$ total attack traffic to upstream protected servers, compared to $0.5031R$ for RCA70 (+43.3 tokens/second, an **8.6% increase in backend attack load**).
  2. **Idle Credit Headroom:** RCA80 shrinks the idle-conditioned reward headroom from $300$ tokens to $200$ tokens (a **33.3% loss in dynamic burst capability**).

---

## 4. Recomputation of the Complete Non-Dominated Frontier

Across all 11 evaluated limiter configurations:

| Configuration | Status | Dominating Configuration(s) |
| :--- | :---: | :---: |
| **TB Large (`tb_large`)** | **Non-Dominated** | — |
| **TB Envelope (`tb_env`)** | **Non-Dominated** | — |
| **Static Dual-Bucket 3/7** | **Non-Dominated** | — (Lowest total adv rate, but catastrophic FRR: 39.46%) |
| **Static Dual-Bucket 7/10** | **Non-Dominated** | — |
| **DREB v1 BM** | **DOMINATED** | **DREB-RCA70**, **DREB-RCA80** |
| **DREB v2 Default** | **DOMINATED** | **DREB-RCA70**, **DREB-RCA80** |
| **DREB-AC BM** | **DOMINATED** | **DREB-MAG** |
| **DREB-MAG** | **Non-Dominated** | — |
| **DREB-RCA60** | **Non-Dominated** | — |
| **DREB-RCA70** | **Non-Dominated** | — (Best trade-off between total attack protection & FRR) |
| **DREB-RCA80** | **Non-Dominated** | — (Best bursty FRR, higher total attack throughput) |

---

## 5. Architectural Recommendation & Conclusion

Both RCA70 and RCA80 are non-dominated, high-performance designs that completely eliminate the over-enforcement defect.

However, **DREB-RCA70** is the mathematically and architecturally preferred recommendation because:
1. **Upstream Protection:** In rate-limiting deployments, the primary objective under adversarial conditions is to bound the *total* volume of requests reaching upstream database and application servers. RCA70 bounds total attack volume to $0.5031R$, whereas RCA80 allows $0.5464R$.
2. **Incentive Alignment:** DREB's core research contribution is dynamic burst tolerance earned through idleness. At $B_{\max} = 0.70C$, clients enjoy a substantial $30\%$ dynamic burst boost upon rest ($1,000$ vs $700$ tokens). At $B_{\max} = 0.80C$, the dynamic headroom shrinks to $20\%$, moving the algorithm closer to a static token bucket.
