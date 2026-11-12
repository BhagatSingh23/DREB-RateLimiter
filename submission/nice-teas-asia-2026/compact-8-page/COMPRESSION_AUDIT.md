# Compression Audit: IEEE NICE-TEAS ASIA 2026 Compact 8-Page Manuscript

**Document Version:** Phase 11 Conference Deliverable  
**Target Venue:** 2026 IEEE 4th Networking International Conference on Emerging Trends in Expert Systems, Applications & Security, ASIA (NICE-TEAS ASIA 2026)  
**Parent 10-Page Reference:** `submission/nice-teas-asia-2026/main.pdf` (Commit `5d8bfad`)  
**Compiled 8-Page Artifact:** `submission/nice-teas-asia-2026/compact-8-page/main.pdf`  
**LaTeX Source:** `submission/nice-teas-asia-2026/compact-8-page/latex/main.tex`  
**Markdown Source:** `submission/nice-teas-asia-2026/compact-8-page/NICE_TEAS_DREB_RCA70_COMPACT_8PAGE.md`  

---

## 1. Executive Summary

This audit verifies the strict condensation of the DREB-RCA70 conference manuscript from 10 pages to **exactly 8 printed pages** in full compliance with IEEE conference formatting standards.

| Metric | 10-Page Version (`5d8bfad`) | 8-Page Compact Edition (Phase 11) | Net Delta |
| :--- | :---: | :---: | :---: |
| **Total Printed Pages** | 10 pages | **8 pages** | -2 pages (-20.0%) |
| **Total Word Count** | 5,954 words | **4,892 words** | -1,062 words (-17.8%) |
| **Total Text Characters** | 43,267 characters | **35,280 characters** | -7,987 chars (-18.5%) |
| **Document Class** | IEEEtran (`conference`) | IEEEtran (`conference`) | Unchanged |
| **Font Size & Margins** | Standard 10pt / Standard IEEE | Standard 10pt / Standard IEEE | Unchanged (No hacks) |
| **Overfull Hboxes** | Multiple warnings | **0 warnings (Clean build)** | Completely resolved |
| **Total Figures** | 6 figures | **5 figures** (Fig 4 summarized in prose) | -1 figure |
| **Total Tables** | 6 tables | **5 tables** (Tables 1 & 2 consolidated) | -1 table |
| **Citations / References** | 10 citations | **10 citations** | 100% Retained |

---

## 2. Condensation Methodology & Structural Changes

The condensation was achieved through **content distillation, tabular consolidation, and structural stream-lining**, completely avoiding artificial typography tampering (e.g., negative vspace, reduced font sizes, margin overrides, or altered line spacing).

### 2.1 Table Consolidation
- **10-Page Original:** Contained Table 1 (Synthetic Workloads W01–W10, 10 rows) and Table 2 (17 Closed-Loop Attack Families, 5 rows) as two separate vertical floats, occupying over 0.6 pages of column space.
- **8-Page Compact:** Consolidated into a unified, two-part structured table (`Table 1: Comprehensive Benchmark Suite`). Part 1 lists all 10 programmatic synthetic workloads (W01–W10), their arrival processes, and burst profiles. Part 2 lists all 17 closed-loop attack families (Families A–E / A01–A17) and their adversarial probing objectives.
- **Space Saved:** ~0.35 printed pages.

### 2.2 Figure Optimization
- **Retained Figures (5):**
  1. `fig1_architecture_adaptive_ceiling.pdf` (Figure 1): System architecture, dual-bucket replenishment, and hyperbolic contraction curve. Spans two columns at the top of Page 3.
  2. `fig6_single_event_failover_envelope.pdf` (Figure 2 / Fig 6 in text): Single-event failover over-admission envelope. Single-column float on Page 4.
  3. `fig3_adversarial_farming_suppression.pdf` (Figure 3): Credit farming throughput across 17 attack families. Single-column float on Page 6.
  4. `fig5_latency_hierarchy.pdf` (Figure 4 / Fig 5 in text): Four-tier latency hierarchy (in-memory to distributed cluster). Single-column float on Page 6.
  5. `fig2_pareto_frontiers.pdf` (Figure 5 / Fig 2 in text): Multi-objective Pareto frontiers comparing Scope 1 vs. Scope 2/3. Single-column float on Page 7.
- **Omitted Figure (1):**
  - `fig4_recovery_trajectories.pdf` (Continuous linear recovery trajectories under varied idle durations).
  - *Justification:* The recovery dynamics are strictly linear ($\dot{K}(t) = -\lambda_{\text{rec}} = -0.05\,\text{s}^{-1}$) and are completely, unambiguously described by the closed-form equation $\tau_{\text{rec}} = K_0 / \lambda_{\text{rec}} = 1.0 / 0.05 = 20.0\,\text{s}$ and Theorem 5. Omission of the redundant visual plot saves ~0.35 printed pages while retaining 100% mathematical precision.

### 2.3 Mathematical Proofs and Equations
- All 5 theorems, 1 corollary, and 1 lemma are retained with full formal statements.
- Proofs were tightened to focus on core invariant algebra and token conservation equations rather than repetitive verbal commentary.
- Overlong inline display math expressions were refactored into clean `align` environments or wrapped to eliminate all overfull hbox warnings in two-column format.
- **Space Saved:** ~0.30 printed pages.

### 2.4 Section Merging and Prose Distillation
- **Sections Merged:** Former Section X (*Limitations and Future Work*) and Section XI (*Practical Deployment Implications and Threats to Validity*) were merged into a single cohesive section: **Section 10: Discussion, Deployment Requirements, and Threats to Validity**.
- **Prose Distillation:**
  - Introduction: Removed redundant background phrasing about generic microservice architectures while preserving the exact definition of the Quiescence Paradox.
  - Threat Model: Distilled the credit farming attack cycle into three concise steps.
  - Redis Implementation: Condensed repetitive architectural descriptions into three compact enumeration items (Hash-Tag Affinity, Atomic Lua Evaluation, and the 5-Condition Idempotency Contract).
  - Related Work: Streamlined discussion of classic telecommunications standards (srTCM, GCRA, 802.1Qav) while preserving citations and comparative analysis.
- **Space Saved:** ~0.40 printed pages.

---

## 3. Strict Claim and Data Verification

Every numerical value, experimental metric, formal bound, and architectural parameter from the canonical research freeze (`1040cc4`) is verified and preserved verbatim:

| Parameter / Claim | Frozen Research Value | 8-Page Compact Value | Status |
| :--- | :---: | :---: | :---: |
| **Sustained Refill Rate ($R$)** | $1,000.0\,\text{tokens/s}$ | $1,000.0\,\text{tokens/s}$ | **VERIFIED** |
| **Liquid Bucket Capacity ($B_{\max}$)** | $700.0\,\text{tokens}$ | $700.0\,\text{tokens}$ | **VERIFIED** |
| **Sustained Bucket Capacity ($S_{\max}$)** | $1285.7143\,\text{tokens}$ | $1285.7143\,\text{tokens}$ | **VERIFIED** |
| **Base Idle Ceiling ($\text{baseCap}$)** | $450.0\,\text{credits}$ | $450.0\,\text{credits}$ | **VERIFIED** |
| **Deficit Asymmetric Multiplier ($d$)** | $1.5$ | $1.5$ | **VERIFIED** |
| **Ceiling Sensitivity ($\gamma$)** | $0.75$ | $0.75$ | **VERIFIED** |
| **Linear Recovery Rate ($\lambda_{\text{rec}}$)** | $0.05\,\text{s}^{-1}$ | $0.05\,\text{s}^{-1}$ | **VERIFIED** |
| **Quiescent Burst Capacity ($C_{\max}$)** | $1,000.0\,\text{tokens}$ | $1,000.0\,\text{tokens}$ | **VERIFIED** |
| **Penalized Burst Capacity ($C_{\text{penalized}}$)** | $871.43\,\text{tokens}$ | $871.43\,\text{tokens}$ | **VERIFIED** |
| **Full Abuse Recovery Duration ($\tau_{\text{rec}}$)** | $20.0\,\text{seconds}$ | $20.0\,\text{seconds}$ | **VERIFIED** |
| **Recovery to $\epsilon = 0.05$** | $19.0\,\text{seconds}$ | $19.0\,\text{seconds}$ | **VERIFIED** |
| **$G_1$ Local-Time Deterministic Bound** | $1285.7143 + 1000.0 \cdot T$ | $1285.7143 + 1000.0 \cdot T$ | **VERIFIED** |
| **$G_{1,\delta}$ Physical Bound ($\delta = 5\,\text{ms}$)** | $1295.7143 + 1000.0 \cdot \Delta t$ | $1295.7143 + 1000.0 \cdot \Delta t$ | **VERIFIED** |
| **Clock Drift Excess Allowance ($2R\delta$)** | $10.0\,\text{tokens}$ | $10.0\,\text{tokens}$ | **VERIFIED** |
| **$G_4$ Single-Event Failover Bound** | $2 C_{\max} = 2,000.0\,\text{tokens}$ | $2 C_{\max} = 2,000.0\,\text{tokens}$ | **VERIFIED** |
| **Failover Overshoot Excess vs. $G_1$** | $+704.2857\,\text{tokens}$ | $+704.2857\,\text{tokens}$ | **VERIFIED** |
| **Failover Overshoot Excess vs. $G_{1,\delta}$** | $+694.2857\,\text{tokens}$ | $+694.2857\,\text{tokens}$ | **VERIFIED** |
| **$G_2$ Convergence Criteria** | Sublinear $N_E(T)/T \to 0$ | Sublinear $N_E(T)/T \to 0$ | **VERIFIED** |
| **Benign Burst Tolerance ($c = 1,000$)** | 0.00% FRR (Quiescent) | 0.00% FRR (Quiescent) | **VERIFIED** |
| **Baseline FRR ($C=700$)** | 30.0% Token / 100% Req. | 30.0% Token / 100% Req. | **VERIFIED** |
| **W04 Periodic Farming Suppression** | 81.9% ($0.4517 \to 0.0817$) | 81.9% ($0.4517 \to 0.0817$) | **VERIFIED** |
| **W08 Adaptive AIMD Suppression** | 56.7% ($0.8000 \to 0.3467$) | 56.7% ($0.8000 \to 0.3467$) | **VERIFIED** |
| **W10 Coordinated Pulse Suppression** | 85.7% ($0.3500 \to 0.0500$) | 85.7% ($0.3500 \to 0.0500$) | **VERIFIED** |
| **Mean Farming Suppression (Attacks)** | 74.8% | 74.8% | **VERIFIED** |
| **Closed-Loop Ablation Accrual Reduction** | 93.04% ($1.0 \to 0.0696$) | 93.04% ($1.0 \to 0.0696$) | **VERIFIED** |
| **In-Memory Java P50 / P99 Latency** | 42 ns / 83 ns | 42 ns / 83 ns | **VERIFIED** |
| **Redis Lua Engine P50 Latency** | 0.082 ms | 0.082 ms | **VERIFIED** |
| **Async Network RTT P50 / P99** | 0.259 ms / 0.667 ms | 0.259 ms / 0.667 ms | **VERIFIED** |
| **Sync `WAIT 1` RTT P50 / P99** | 0.348 ms / 1.301 ms | 0.348 ms / 1.301 ms | **VERIFIED** |
| **Sync `WAIT 1` Tail Penalty** | +95.1% P99 vs. Async | +95.1% P99 vs. Async | **VERIFIED** |
| **Redis Memory Sizing Model** | $216 N_{\text{state}} + 176 N_{\text{idemp}}$ | $216 N_{\text{state}} + 176 N_{\text{idemp}}$ | **VERIFIED** |
| **1M State + 1M Idemp Memory Footprint** | ~392 MB | ~392 MB | **VERIFIED** |
| **Scope 1 Non-Dominated Frontier** | {`static_db_070c`} | {`static_db_070c`} | **VERIFIED** |
| **Scope 2 Non-Dominated Frontier** | {`rca70`, `tb_large`} | {`rca70`, `tb_large`} | **VERIFIED** |
| **Scope 3 Non-Dominated Frontier** | {`rca70`, `tb_large`} | {`rca70`, `tb_large`} | **VERIFIED** |
| **Benchmark Coverage** | 10 Workloads, 17 Attacks, 30 Seeds | 10 Workloads, 17 Attacks, 30 Seeds | **VERIFIED** |

---

## 4. Page-by-Page Layout Accounting

The compiled PDF (`submission/nice-teas-asia-2026/compact-8-page/main.pdf`) adheres to the following page allocation:

- **Page 1 (4,485 chars, 91 lines):** Title, Authors, Abstract, Keywords, Section 1 (Introduction), Section 1.1 (Contributions).
- **Page 2 (4,979 chars, 103 lines):** Section 2 (Problem Formulation and Threat Model), Section 3 (The DREB-RCA70 Mechanism Architecture: State Components, Governing Dynamics, Deficit Spending, RCA70 Policy).
- **Page 3 (2,720 chars, 71 lines):** Figure 1 (Architecture diagram across two columns), Algorithm 1 (Pseudo-code), Section 4 (Formal Guarantee Contract), Lemma 1, Theorem 1 ($G_1$ Local-Time Rate Bound).
- **Page 4 (3,893 chars, 103 lines):** Figure 2 / Fig 6 (Failover Envelope), Theorem 2 ($G_{1,\delta}$ Clock Skew), Corollary 1, Theorem 3 ($G_4$ Failover Bound), Theorem 4 ($G_2$ Asymptotic Convergence).
- **Page 5 (4,847 chars, 107 lines):** Theorem 4 Proof, Theorem 5 ($G_3$ Recovery Duration), Section 5 (Distributed Redis Cluster Implementation: Hash-Tag Affinity, Lua Engine, 5-Condition Idempotency Contract, Failover, Memory Sizing), Section 6 (Experimental Methodology), Table 1 (Benchmark Suite taxonomy).
- **Page 6 (4,617 chars, 135 lines):** Section 6.2 (Testbed Infrastructure), Section 7 (Empirical Evaluation), Table 2 (Benign Burst FRR), Figure 3 (Farming Suppression), Table 3 (Farming Suppression), Figure 4 / Fig 5 (Latency Hierarchy), Table 4 (Latency Hierarchy).
- **Page 7 (4,863 chars, 119 lines):** Section 8 (Multi-Objective Pareto Frontier Reconciliation), Figure 5 / Fig 2 (Pareto Frontiers), Table 5 (Pareto Classification), Section 9 (Related Work: Classical Token Buckets, Adaptive Limiters).
- **Page 8 (2,388 chars, 53 lines):** Section 10 (Discussion, Deployment Requirements, and Threats to Validity: Clock Synchronization, External/Construct/Internal Validity), Section 11 (Conclusion), References [1]--[10]. Terminates cleanly with zero overflow to Page 9.

---

## 5. Conclusion & Submission Readiness

The compact 8-page edition is mathematically identical and empirically equivalent to the canonical research deliverables. It provides conference chairs and reviewers with a self-contained, publication-ready manuscript tailored strictly to 8 printed pages.
