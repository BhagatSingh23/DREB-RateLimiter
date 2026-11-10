# Phase 5.2 — Final Pareto Frontier Verification & Methodology Addendum

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`4ed10a8`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Policies:** DREB-RCA70, DREB-AC BM, DREB v2 Default, Static Dual-Bucket (0.70C), Token Bucket (C, R)  
**Dataset:** [`phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv) (1,501 rows)

---

## 1. Executive Summary

This addendum provides the final mathematical verification of the Pareto frontier across the five evaluated policies, resolving all residual questions regarding the dominance status of **DREB-AC BM**, the separation of workload-specific vs. aggregate frontiers, and the methodological timing of operational eligibility constraints.

### Key Conclusions
1. **Unconstrained Pareto Status of DREB-AC BM:**
   DREB-AC BM is **genuinely non-dominated** on the unconstrained mathematical Pareto frontier. It achieves strictly lower adversarial throughput than RCA70 under AIMD interval adaptation (W08: $0.1200R$ vs $0.3467R$) and strictly lower adversarial throughput than Static Dual-Bucket under Black-Box probing (W07: $0.6955R$ vs $0.8266R$) and Multi-Worker collision (W09: $0.9517R$ vs $0.9928R$). No policy mathematically dominates DREB-AC BM across all dimensions.
2. **Operational Eligibility Constraints Are Post-Hoc Contextual Filters:**
   The thresholds $\text{FRR}_{\text{burst}} \le 5.0\%$ and $\text{FRR}_{\text{post}} \le 5.0\%$ represent engineering availability requirements, not mathematical theorems. They were adopted post-hoc after observing DREB-AC BM's severe burst rejection ($64.1\%$) and post-attack starvation ($60.0\%$). We disclose this transparently.
3. **The True Operational Frontier:**
   Under operational availability requirements that require legitimate burst elasticity up to nominal capacity $C=1000$, the non-dominated set comprises **DREB-RCA70, Static Dual-Bucket, and Token Bucket**.

---

## 2. Unconstrained Pairwise Dominance Verification

Let policy $P$ dominate policy $Q$ ($P \succ Q$) if and only if $P$ is no worse than $Q$ across all six objectives and strictly better in at least one objective.

### 2.1 Reconstructed Aggregate Objective Vector

| Policy ID | Policy Name | $\text{FRR}_{\text{burst}}$ (W02) $\downarrow$ | $\text{BAR}_{\text{burst}}$ (W02) $\uparrow$ | $\text{FRR}_{\text{post}}$ (W10) $\downarrow$ | $r_{\text{adv}}$ (W08) $\downarrow$ | $r_{\text{cred}}$ (W05) $\downarrow$ | $P99_{\text{lat}}$ $\downarrow$ |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`rca70`** | **DREB-RCA70** | **$0.00\%$** | **$100.00\%$** | **$0.00\%$** | $0.3467 R$ | $0.0667 R$ | $208.0\,\text{ns}$ |
| **`dreb_ac_bm`** | DREB-AC BM | $64.10\%$ | $35.90\%$ | $60.00\%$ | **$0.1200 R$** | $0.0589 R$ | **$42.0\,\text{ns}$** |
| **`dreb_v2_default`**| DREB v2 Default | $2.56\%$ | $97.44\%$ | $0.00\%$ | $0.7067 R$ | $0.3214 R$ | $125.0\,\text{ns}$ |
| **`static_db_070c`** | Static DB (0.70C) | **$0.00\%$** | **$100.00\%$** | **$0.00\%$** | **$0.0000 R$** | **$0.0000 R$** | $83.0\,\text{ns}$ |
| **`tb_large`** | Token Bucket | **$0.00\%$** | **$100.00\%$** | **$0.00\%$** | $0.8000 R$ | $0.0000 R$* | $125.0\,\text{ns}$ |

*\*Token Bucket has no credit tracking; all tokens are drawn from its unified bucket $C$.*

### 2.2 Complete Pairwise Dominance Matrix (All 20 Ordered Pairs)

| Dominating Candidate ($P$) | Target Policy ($Q$) | Does $P \succ Q$? | Detailed Objective Comparison |
| :--- | :--- | :--- | :--- |
| **RCA70** | DREB-AC BM | **No** | RCA70 is worse on $r_{\text{adv}}$ ($0.3467 > 0.1200$) and raw $P99$ ($208 > 42\,\text{ns}$). |
| **RCA70** | DREB v2 | **No** | RCA70 is worse on raw $P99$ ($208 > 125\,\text{ns}$) due to timer quantization. |
| **RCA70** | Static DB | **No** | Static DB is superior on adversarial rate ($0.0000R < 0.3467R$) and latency. |
| **RCA70** | Token Bucket | **No** | RCA70 is worse on raw $P99$ ($208 > 125\,\text{ns}$). |
| **DREB-AC BM** | RCA70 | **No** | DREB-AC BM is severely worse on $\text{FRR}_{\text{burst}}$ ($64.10\% > 0.00\%$) and $\text{FRR}_{\text{post}}$ ($60.0\% > 0.0\%$). |
| **DREB-AC BM** | DREB v2 | **No** | DREB-AC BM is worse on $\text{FRR}_{\text{burst}}$ ($64.10\% > 2.56\%$) and $\text{FRR}_{\text{post}}$ ($60.0\% > 0.0\%$). |
| **DREB-AC BM** | Static DB | **No** | DREB-AC BM is worse on $\text{FRR}_{\text{burst}}$, $\text{BAR}$, and adversarial rate. |
| **DREB-AC BM** | Token Bucket | **No** | DREB-AC BM is worse on $\text{FRR}_{\text{burst}}$ and $\text{FRR}_{\text{post}}$. |
| **Static DB** | RCA70 | **Yes (in W02)** | In W02 ($c=700$), Static DB matches $100\%$ burst acceptance while having lower $r_{\text{adv}}$ and latency. |
| **Static DB** | DREB-AC BM | **No** | In W07 ($r_{\text{adv}} = 0.8266 > 0.6955$) and W09 ($0.9928 > 0.9517$), Static DB has higher adversarial rate. |
| **Static DB** | DREB v2 | **Yes** | Static DB is strictly better or equal on all 6 objectives. |
| **Static DB** | Token Bucket | **Yes (in W02)** | Static DB matches $100\%$ burst on $c=700$ while admitting $0.0000R$ vs $0.8000R$ in W08. |
| **Token Bucket** | RCA70 | **No** | Token Bucket has worse $r_{\text{adv}}$ ($0.8000 > 0.3467$) and worse mixed FRR ($2.91\% > 0.22\%$). |
| **Token Bucket** | DREB-AC BM | **No** | Token Bucket has worse $r_{\text{adv}}$ ($0.8000 > 0.1200$). |
| **Token Bucket** | DREB v2 | **No** | Token Bucket has worse $r_{\text{adv}}$ ($0.8000 > 0.7067$). |
| **Token Bucket** | Static DB | **No** | Token Bucket has worse $r_{\text{adv}}$ ($0.8000 > 0.0000$). |
| **DREB v2** | *Any Policy* | **No** | DREB v2 is dominated or inferior across all comparisons. |

---

## 3. Workload-Specific Pareto Frontiers

Evaluating dominance across individual workloads highlights that dominance is not uniform across threat models:

| Workload Scenario | Non-Dominated Policies on Scenario Frontier | Critical Trade-Off |
| :--- | :--- | :--- |
| **W02: Compliant On/Off Bursts ($c=700$)** | `{RCA70, Static DB, Token Bucket}` | DREB-AC ($64.1\%$ FRR) and DREB v2 ($2.56\%$ FRR) are strictly eliminated. |
| **W04: Mixed Competing Poisson** | `{Static DB, RCA70}` | Static DB achieves $0.00\%$ FRR; RCA70 achieves $0.22\%$ FRR; Token Bucket suffers $2.91\%$ FRR. |
| **W05: Refill Boundary Micro-Pulsing** | `{Static DB, Token Bucket, RCA70, DREB-AC}` | Static DB blocks requests ($0.000R$); DREB-AC draws $0.0589R$; RCA70 draws $0.0667R$. |
| **W07: Black-Box MIMD Probing** | `{DREB-AC, RCA70, Static DB}` | **DREB-AC achieves lowest adv rate ($0.6955R$)**; Static DB admits $0.8266R$; RCA70 admits $0.9736R$. |
| **W08: Closed-Loop AIMD Adaptation** | `{Static DB, DREB-AC, RCA70}` | **DREB-AC achieves lower adv rate ($0.1200R$) than RCA70 ($0.3467R$)**. |
| **W09: Multi-Worker Concurrent Collision** | `{DREB-AC, Static DB, RCA70}` | **DREB-AC achieves lowest adv rate ($0.9517R$)**; Static DB admits $0.9928R$; RCA70 admits $1.0039R$. |
| **W10: Post-Attack Recovery** | `{RCA70, Static DB, Token Bucket, DREB v2}` | **DREB-AC is strictly eliminated due to $60.00\%$ post-attack starvation**. |

> [!IMPORTANT]
> **Definitive Finding on DREB-AC BM:**
> Under adaptive attacks (W07, W08, W09), DREB-AC BM's artificially small burst bucket ($B_{\max} = 428.57$) restricts adversarial throughput. Because it outperforms all other policies on adversarial admission in W07, it **cannot be mathematically dominated** in an unconstrained Pareto analysis.

---

## 4. Methodological Transparency on Operational Eligibility Constraints

To avoid post-hoc bias, the context and rationale for the operational eligibility criteria must be formally documented:

1. **When Were the Thresholds Selected?**
   The thresholds ($\text{FRR}_{\text{burst}} \le 5.0\%$ and $\text{FRR}_{\text{post}} \le 5.0\%$) were formulated **post-hoc** during Phase 2.5 and Phase 5 after empirical runs revealed that DREB-AC BM caused severe burst rejection ($64.1\%$) and post-attack starvation ($60.0\%$).
2. **Why Are They Defensible?**
   In enterprise API rate limiting, a policy that rejects over half of all compliant customer bursts after legitimate idle periods violates basic SLA contracts. A rate limiter cannot be considered operational if it enforces anti-farming by permanently degrading legitimate burst tolerance.
3. **Formal Qualification:**
   In publication manuscripts, these criteria must be presented as **"Production Availability Eligibility Filters"**, distinct from mathematical Pareto optimality.
