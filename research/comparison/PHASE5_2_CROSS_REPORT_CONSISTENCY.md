# Phase 5.2 — Cross-Report Consistency & Parameter Audit

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`4ed10a8`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Artifacts:** Phase 2, 2.5, 3, 3.1, 4, 5, 5.1, 5.2 Reports and Datasets

---

## 1. Executive Summary

This audit establishes a complete, cross-phase reconciliation of all parameter values, mathematical invariants, theoretical guarantees, and empirical claims formulated throughout the research trajectory.

To maintain historical integrity, earlier documents remain unmodified. This addendum documents the exact original value, the audited value, the supporting evidence, and the finalized status for publication.

---

## 2. Comprehensive Cross-Report Audit Table

| Core Dimension | Original Formulated Value | Final Audited Value | Primary Evidence | Status |
| :--- | :--- | :--- | :--- | :--- |
| **RCA70 Allocation Fraction ($\alpha$)** | $\alpha = 0.70$ ($B_{\max} = 700.0$) | $\alpha = 0.70$ ($B_{\max} = 700.0$) | Phase 2 Sweep, Phase 3 Ablation | **Confirmed** |
| **RCA70 Burst Refill Rate ($R_{\text{burst}}$)** | $R_{\text{burst}} = 1666.67\,\text{tokens/s}$ | $R_{\text{burst}} = 1666.67\,\text{tokens/s}$ | `DrebRcaPolicyFactory.java` | **Confirmed** |
| **RCA70 Sustained Capacity ($S_{\max}$)** | $S_{\max} = \frac{9}{7}C \approx 1285.71$ | $S_{\max} = \frac{9}{7}C \approx 1285.71$ | Guarantee G1 derivation | **Confirmed** |
| **RCA70 Sustained Rate ($S_{\text{rate}}$)** | $S_{\text{rate}} = R = 1000.0\,\text{tokens/s}$ | $S_{\text{rate}} = R = 1000.0\,\text{tokens/s}$ | Phase 2 Parameter Sweep | **Confirmed** |
| **Adaptive Ceiling Parameters** | $\text{baseCap}=450, \text{baseDecay}=1.5, \beta=0.75, \lambda_{\text{rec}}=0.05$ | $\text{baseCap}=450, \text{baseDecay}=1.5, \beta=0.75, \lambda_{\text{rec}}=0.05$ | Phase 3 Ablation Matrix | **Confirmed** |
| **Guarantee G1 Cumulative Envelope** | $A(T) \le S_{\max} + R \cdot T$ | $A(T) \le S_{\max} + R \cdot T$ ($Viol = 0.00$) | Checked continuously at 6,000 steps | **Confirmed** |
| **Finite-Window Rate Bound ($T=60\,\text{s}$)** | $r_{\text{avg}} \approx 1.02 R$ (questioned as violation) | $r_{\text{avg}} \le 1 + \frac{S_{\max}}{R \cdot T} = 1.02143 R$ | Exact derivation in Phase 5 / 5.1 | **Confirmed** |
| **Guarantee G2 Minimum Idle Time** | $\tau_{\min} = 0.720\,\text{s}$ for burst $C=1000$ | $\tau_{\min} = 0.720\,\text{s}$ (under $K_0=0, S_0 \ge 280$) | Phase 3.1 Initial-State Audit | **Confirmed with Qualification** |
| **Guarantee G3 Recovery Invariant** | Decouples base burst from abuse score | Compliant bursts $c \le 700$ require $0$ credit ($\text{FRR} = 0.00\%$) | Workload W10 ($100\%$ burst acc) | **Confirmed** |
| **Theorem 2 Anti-Farming Bound** | $\bar{R}_{\text{credit}} \le \frac{\text{baseCap}}{\bar{D}(1 + \beta K_{\text{ss}})}$ | Holds under credit-accrual episodes | Phase 1.3 / 1.3.1 Proof Closure | **Confirmed** |
| **Credit-Funded Adversarial Throughput** | Restricted to $< 0.07 R$ | Clamped to $0.0136 R - 0.0667 R$ across all attacks | Phase 4 & Phase 5 datasets | **Confirmed** |
| **DREB v2 Credit Leakage Multiplier** | $4.9\times$ to $24.1\times$ higher than RCA70 | **$4.82\times$ to $14.29\times$** (workload-paired) | $24.1\times$ retracted as un-paired cross-workload artifact | **Corrected** |
| **Pareto Dominance of RCA70** | "RCA70 dominates DREB-AC BM" | Non-dominated in unconstrained space; dominant on Operationally Eligible Frontier | DREB-AC achieves $0.1200R$ in W08; fails $\text{FRR} \le 5\%$ operational filter | **Corrected** |
| **In-Memory State Footprint (JVM)** | "40 bytes per client (< 40 MB / 1M)" | **$150.6 - 165.3\,\text{bytes}$ per client** in `ConcurrentHashMap` | 40 bytes is primitive payload only; retained heap includes headers & keys | **Corrected** |
| **Distributed State Footprint (Redis)** | "200–250 bytes estimated" | **$192 - 205\,\text{bytes / client}$** | Verified on Redis 7.4.11 listpack | **Confirmed** |
| **Decision Latency (P50 / P99)** | P50: $84\,\text{ns}$, P99: $292\,\text{ns}$ | Reclassified as quantized microbenchmark observations ($2 - 7$ ticks) | Mach timer quantum is $41.67\,\text{ns}$ ($24\,\text{MHz}$ clock); timer overhead $\approx 20\,\text{ns}$ | **Confirmed with Qualification** |
| **Single-Threaded Throughput Claim** | "$> 11.9\,\text{million ops/sec}$" | Retracted as unrepresentative of concurrent multi-key production | Simple reciprocal of median in-memory latency is invalid | **Rejected** |

---

## 3. Standardization of Terminology and Units

To avoid ambiguity across publications and research repositories, the following notation is standardized:

1. **Burst Capacity ($C$):** Nominal maximum post-idle burst capacity ($C = 1000\,\text{tokens}$).
2. **Burst Bucket Allocation ($\alpha$):** Fraction of nominal burst assigned to primary bucket ($\alpha = 0.70 \implies B_{\max} = 700\,\text{tokens}$).
3. **Sustained Bucket Ratio:** $S_{\max} = \frac{9}{7}C \approx 1285.714\,\text{tokens}$.
4. **Sustained Contract Rate ($R$):** Long-run average rate limit ($R = 1000\,\text{tokens/s}$).
5. **Burst Refill Rate ($R_{\text{burst}}$):** Refill rate of primary burst bucket ($R_{\text{burst}} = \frac{5}{3}R \approx 1666.667\,\text{tokens/s}$).
6. **Exchange Decay Rate ($\text{baseDecay}$):** Credit cost per token deficit ($\text{baseDecay} = 1.5\,\text{credits/token}$).
7. **Adaptive Abuse Ceiling Discount ($\beta$):** Attenuation coefficient ($\beta = 0.75$).
8. **Abuse Recovery Exponential Rate ($\lambda_{\text{rec}}$):** Half-life decay rate ($\lambda_{\text{rec}} = 0.05\,\text{s}^{-1} \implies t_{1/2} \approx 13.86\,\text{s}$).
9. **Normalized Throughput ($r$):** All throughput figures are normalized by $R$: $r = \frac{\text{Tokens}}{R \cdot T}$.
