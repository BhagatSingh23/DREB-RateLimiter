# Phase 5.1 — Credit-Leakage Claim Reconciliation & Workload Audit

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`4c26bce`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Datasets:** [`phase4_adversarial_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/adversarial/phase4_adversarial_raw.csv), [`phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv)  
**Verification Test:** [`Phase51CorrectiveAuditTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase51CorrectiveAuditTest.java)

---

## 1. Executive Summary of Discrepancy

A comparative audit of the credit-leakage claims between Phase 4 and Phase 5 identified an apparent inconsistency:
- **Phase 4 Stated:** DREB v2 leaks **$4.9\times$ to $24.1\times$** more credit than DREB-RCA70.
- **Phase 5 Stated:** DREB v2 leaks **$4.8\times$ to $12.8\times$** more credit than DREB-RCA70.

### Audit Finding
The discrepancy arises from a **methodological error in Phase 4**:
1. The **$24.1\times$ figure** reported in Phase 4 was an **un-paired, cross-workload aggregate artifact**. It was calculated by taking the maximum credit drawn by DREB v2 under Attack C1 AIMD ($0.3281R$) and dividing it by the minimum credit drawn by RCA70 under Attack B1 White-Box ($0.0136R$):
   $$\text{Cross-Workload Ratio} = \frac{\max_{\text{Attacks}}(r_{\text{cred}}^{\text{v2}})}{\min_{\text{Attacks}}(r_{\text{cred}}^{\text{RCA70}})} = \frac{0.3281 R}{0.0136 R} = \mathbf{24.125\times}$$
2. When computed on a **strictly paired, workload-by-workload basis** (matching the identical arrival realization for both numerator and denominator), the true ratio of DREB v2 credit leakage relative to RCA70 ranges from:
   $$\mathbf{4.82\times} \quad \text{to} \quad \mathbf{14.29\times}$$
3. Phase 5 evaluated a subset of 10 workloads, where the paired ratio spanned from **$4.82\times$** (W05 Timing Farming) to **$12.83\times$** (W09 Multi-Worker Collision) and **$14.29\times$** (W10 Post-Attack Recovery).

---

## 2. Workload-by-Workload Credit Leakage Audit (Phase 4 vs Phase 5)

The table below provides the definitive paired reconciliation across all scenarios in Phase 4 and Phase 5, listing the exact numerator (DREB v2), denominator (RCA70), and paired ratio:

| Scenario ID | Scenario Name | RCA70 Credit Rate ($r_{\text{cred}}^{\text{RCA}}$) | DREB v2 Credit Rate ($r_{\text{cred}}^{\text{v2}}$) | Workload-Paired Ratio ($\frac{\text{v2}}{\text{RCA}}$) | Present in Phase 5? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **A1 / W05** | **Micro-Pulsing / Timing Farming** | $0.0667 R$ | $0.3214 R$ | **$4.82\times$** | Yes (as W05) |
| **A2** | Refill Boundary Jitter | $0.0351 R$ | $0.3151 R$ | **$8.99\times$** | No |
| **A3** | Periodic Full Burst ($\tau = 0.720\,\text{s}$) | $0.0250 R$ | $0.2571 R$ | **$10.29\times$** | No |
| **A4** | Phase-Shifted Sinusoidal Wait | $0.0250 R$ | $0.2476 R$ | **$9.90\times$** | No |
| **B1 / W06** | **White-Box Optimal Extraction** | $0.0136 R$ | $0.1387 R$ | **$10.19\times$** | Yes (as W06) |
| **B2 / W07** | **Black-Box Adaptive Inferred Probe** | $0.0433 R$ | $0.2741 R$ | **$6.33\times$** | Yes (as W07) |
| **C1 / W08** | **AIMD Adaptive Interval Attack** | $0.0433 R$ | $0.3281 R$ | **$7.57\times$** | Yes (as W08) |
| **C2** | Binary Search Cost Adaptation | $0.0349 R$ | $0.2864 R$ | **$8.21\times$** | No |
| **C3** | Credit Maximizer Bound | $0.0250 R$ | $0.2571 R$ | **$10.29\times$** | No |
| **D1 / W04** | **Mixed Competing Traffic** | $0.0245 R$ | $0.1686 R$ | **$6.88\times$** | Yes (as W04) |
| **D2 / W09** | **Multi-Worker Concurrent Collision** | $0.0144 R$ | $0.1849 R$ | **$12.83\times$** | Yes (as W09) |
| **D3 / W10** | **Post-Attack Recovery Interval** | $0.0150 R$ | $0.2143 R$ | **$14.29\times$** | Yes (as W10) |
| **E1** | Near-$B_{\max}$ Boundary ($c=701$) | $0.0005 R$ | $0.3076 R$ | **$636.89\times$**\* | No |
| **E2** | Near-$C$ Capacity Boundary | $0.0250 R$ | $0.2472 R$ | **$9.89\times$** | No |
| **E5** | Bimodal Alternation ($\{1, 1000\}$) | $0.0250 R$ | $0.2762 R$ | **$11.05\times$** | No |

*\*Note on Attack E1: Because RCA70's $B_{\max} = 700$, a request of $c=701$ requires only 1 token of idle credit ($r_{\text{cred}} = 0.0005R$). DREB v2's $B_{\max} = 428.57$ requires $272.43$ tokens of credit ($r_{\text{cred}} = 0.3076R$), producing a mathematical ratio of $636.89\times$. Because this reflects the structural boundary of $B_{\max}$ rather than continuous credit farming, it is excluded from general farming ratio bounds.*

---

## 3. Methodological Reconciliation & Formal Correction

1. **Rejection of the Cross-Workload Ratio:**
   Dividing the numerator from Attack C1 by the denominator from Attack B1 is scientifically invalid because it compares performance under two completely different threat models. The claim that DREB v2 leaks "up to $24.1\times$" more credit is formally **retracted**.
2. **Certified Workload-Paired Ratio Range:**
   Under all evaluated continuous credit-farming attacks (Families A, B, C, D), DREB-RCA70 reduces idle credit consumption relative to DREB v2 Default by:
   $$\mathbf{4.8\times \quad \text{to} \quad 14.3\times}$$
   - **Minimum Reduction:** $4.82\times$ under Attack A1 / W05 (refill boundary micro-pulsing).
   - **Median Reduction:** $9.90\times$ across all farming strategies.
   - **Maximum Reduction:** $14.29\times$ under Attack D3 / W10 (post-attack recovery window).
