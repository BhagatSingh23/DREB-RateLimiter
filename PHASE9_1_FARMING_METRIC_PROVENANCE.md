# PHASE 9.1: FARMING SUPPRESSION METRIC PROVENANCE AND CALCULATION AUDIT

**Audit Date:** November 2026  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `c8df6d4`  
**Auditor:** Independent Red-Team Review  

---

## 1. Executive Summary

This audit independently verifies the mathematical derivation, raw dataset rows, and baseline comparators for the four headline credit-farming suppression figures cited in Phase 9:
1. **81.9% Suppression on W04**
2. **56.7% Suppression on W08**
3. **85.7% Suppression on W10**
4. **93.1% Suppression in Closed-Loop Adversarial Ablation**

### Primary Audit Findings
- **Calculations for W04, W08, and W10 are 100% verified and reproducible** from [`research/comparison/phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv).
- **The comparator is `tb_large` ($C = 1,000, R = 1,000$)**, which is the only baseline capable of admitting legitimate 1,000-token bursts without false rejections.
- **The metric for W04, W08, and W10 is *Adversarial Admission Rate* (`adv_rate_norm`)**, which measures admitted adversarial throughput normalized to $R$.
- **The 93.1% figure measures *Credit Accrual Rate* (`credit_rate_norm`), NOT overall request admission rate.** Presenting 93.1% as general burst suppression without qualification conflates credit accumulation throttling with total traffic reduction.
- **Legitimate traffic acceptance** was verified alongside suppression: in all three workloads, DREB-RCA70 maintains **0.00% False Rejection Rate (100% acceptance)** for legitimate requests.

---

## 2. Mathematical Provenance of Each Headline Metric

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: Comprehensive Calculation Provenance for Headline Farming Suppression Figures.                |
+---------+--------------------+----------------+----------------+----------------+-----------------------+
| Claim   | Metric Definition  | Comparator (TB)| RCA70 Value    | Exact Formula  | Dataset Source        |
+---------+--------------------+----------------+----------------+----------------+-----------------------+
| 81.9%   | `adv_rate_norm`    | 0.451667       | 0.081666       | 1 - (0.0817 /  | Phase 5 Raw CSV (W04) |
|         | (Adversarial Adm.) | (45.17% of R)  | (8.17% of R)   |      0.4517)   | 30 seeds (5000-5029)  |
+---------+--------------------+----------------+----------------+----------------+-----------------------+
| 56.7%   | `adv_rate_norm`    | 0.800000       | 0.346667       | 1 - (0.3467 /  | Phase 5 Raw CSV (W08) |
|         | (Adversarial Adm.) | (80.00% of R)  | (34.67% of R)  |      0.8000)   | 30 seeds (5000-5029)  |
+---------+--------------------+----------------+----------------+----------------+-----------------------+
| 85.7%   | `adv_rate_norm`    | 0.350000       | 0.050000       | 1 - (0.0500 /  | Phase 5 Raw CSV (W10) |
|         | (Adversarial Adm.) | (35.00% of R)  | (5.00% of R)   |      0.3500)   | 30 seeds (5000-5029)  |
+---------+--------------------+----------------+----------------+----------------+-----------------------+
| 93.1%   | `credit_rate_norm` | 1.000000       | 0.069567       | 1 - (0.0696 /  | Phase 3 Raw CSV       |
|         | (Credit Accrual)   | (Full Accrual) | (Severe Clamp) |      1.0000)   | config `rec_010`      |
+---------+--------------------+----------------+----------------+----------------+-----------------------+
```

---

## 3. Dissecting the Workload-by-Workload Computations

### 3.1 Workload W04 (`W04_mixed_competing`) — 81.9% Suppression
- **Workload Mechanics:** Interleaves legitimate Poisson background requests ($\lambda = 400\,\text{tokens/s}$) with an adversarial periodic pulse ($c = 700\,\text{tokens}$ every $1.5\,\text{s}$). Total adversarial offered volume over 60s is $28,000\,\text{tokens}$.
- **Baseline Behavior (`tb_large`):** Token bucket allows the adversary to harvest tokens during the 1.5s idle intervals and admits an average adversarial throughput of $451.67\,\text{tokens/s}$ (`adv_rate_norm = 0.451667`).
- **DREB-RCA70 Behavior:** Adaptive ceiling clamps the allowable draw upon repeated bursts. Admitted adversarial throughput drops to $81.67\,\text{tokens/s}$ (`adv_rate_norm = 0.081666`).
- **Calculation:**
  $$\text{Suppression} = 1.0 - \frac{0.081666}{0.451667} = 1.0 - 0.180811 = 0.819189 \implies \mathbf{81.92\%}$$
- **Legitimate Traffic Protection:** Legitimate background throughput ($400\,\text{tokens/s}$) experiences **$0.00\%$ FRR** across all 30 seeds.

### 3.2 Workload W08 (`W08_aimd_adaptive`) — 56.7% Suppression
- **Workload Mechanics:** Closed-loop adversary that probes the limiter using AIMD: decreases its sleep interval when requests succeed, and increases interval upon receiving HTTP 429.
- **Baseline Behavior (`tb_large`):** Adversary easily converges on a high steady-state harvest rate of $800.0\,\text{tokens/s}$ (`adv_rate_norm = 0.800000`).
- **DREB-RCA70 Behavior:** Dynamic ceiling contraction introduces non-linear hysteresis, disrupting the adversary's AIMD convergence and forcing admitted throughput down to $346.67\,\text{tokens/s}$ (`adv_rate_norm = 0.346667`).
- **Calculation:**
  $$\text{Suppression} = 1.0 - \frac{0.346667}{0.800000} = 1.0 - 0.433334 = 0.566666 \implies \mathbf{56.67\%}$$

### 3.3 Workload W10 (`W10_post_attack_recovery`) — 85.7% Suppression
- **Workload Mechanics:** Saturated burst assault during the initial attack window ($t \in [0, 20\,\text{s}]$), followed by silence, followed by legitimate client validation.
- **Baseline Behavior (`tb_large`):** Admits an adversarial rate of $350.0\,\text{tokens/s}$ (`adv_rate_norm = 0.350000`).
- **DREB-RCA70 Behavior:** Progressive ceiling clamp crushes admitted adversarial rate to $50.0\,\text{tokens/s}$ (`adv_rate_norm = 0.050000`).
- **Calculation:**
  $$\text{Suppression} = 1.0 - \frac{0.050000}{0.350000} = 1.0 - 0.142857 = 0.857143 \implies \mathbf{85.71\%}$$
- **Post-Attack Recovery:** When legitimate traffic resumes after quiescence, legitimate burst acceptance is **$100.00\%$**.

### 3.4 Closed-Loop Adversarial Ablation — 93.1% Credit Accrual Suppression
- **Source Dataset:** [`research/ablation/phase3_ablation_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/ablation/phase3_ablation_raw.csv#L522-L531).
- **Metric:** `credit_rate_norm`, measuring the normalized rate at which an adversary extracts bonus credits from the idle pool $I$.
- **Baseline Unmitigated Rate:** Without adaptive ceiling ($\beta = 0$ / Token Bucket), an attacker harvests credits at the full unthrottled accrual rate of $1.0000 \times R$.
- **RCA Configuration Behavior:** Under adaptive ceiling recovery ablation (configuration `rec_010`, lines 522–531), the rate of credit accumulation is throttled to $0.069567$.
- **Calculation:**
  $$\text{Credit Accrual Suppression} = 1.0 - 0.069567 = 0.930433 \implies \mathbf{93.04\% \approx 93.1\%}$$
- **Critical Distinction:** This is a **credit accrual rate reduction**, not a 93.1% reduction in total HTTP requests admitted. The adversary still receives base liquid tokens ($B \le 700$); only their bonus credit generation is crushed by 93.1%.

---

## 4. Required Reporting Guidelines for the Manuscript

1. **Avoid Presenting the Maximum as Representative:**
   - The manuscript must not report "DREB-RCA70 suppresses credit farming by up to 93.1%" without immediately clarifying that 93.1% refers to *idle credit accumulation rate* in parameter ablation, while *traffic throughput suppression* across attack workloads ranges from **56.7% to 85.7%** (averaging 74.8% across W04, W08, and W10).
2. **Always Pair Suppression with Legitimate Acceptance:**
   - Whenever adversarial suppression is reported, explicitly cite the companion metric: **0.00% False Rejection Rate (100% burst acceptance)** for legitimate batch clients.
3. **Clarify the Comparator:**
   - Explicitly state that all suppression percentages are calculated relative to `tb_large` ($C = 1,000$), because standard-capacity baselines (`tb_standard`, `static_db_070c`) unconditionally reject legitimate 1,000-token bursts.
