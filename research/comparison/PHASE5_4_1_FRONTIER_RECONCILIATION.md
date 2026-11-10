# Phase 5.4.1 — Frontier Reconciliation Across Scopes & Operational Boundaries

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`62a3774`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Policies:** DREB-RCA70, Static Dual-Bucket (0.70C), Token Bucket (C, R), DREB-AC BM, DREB v2 Default  
**Predecessors:** [`PHASE5_4_1_REQUEST_SEMANTICS_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/PHASE5_4_1_REQUEST_SEMANTICS_AUDIT.md), [`PHASE5_4_1_SCOPE1_PARETO_VERIFICATION.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/PHASE5_4_1_SCOPE1_PARETO_VERIFICATION.md)  
**Verification Test Suite:** [`Phase541RequestSemanticsTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase541RequestSemanticsTest.java)

---

## 1. Executive Summary & Assessment of Phase 5.4 Frontiers

The independent review requested an explicit verification of whether the operational Pareto frontiers derived in Phase 5.4 are:
- Confirmed;
- Confirmed with corrected wording or metrics; or
- Not supported by the available evidence.

### Formal Assessment Statement:
> **The Scope-Delineated Operational Frontiers are CONFIRMED WITH CORRECTED METRICS & SEMANTICS:**
> 1. **Scope 1 ($c \le 700$):** **CONFIRMED.** Static Dual-Bucket strictly dominates Token Bucket, RCA70, and DREB v2 under workload-matched 6D vectors across both P99 and P50 latency measures. Operational Frontier = `{"static_db_070c"}`.
> 2. **Scope 2 ($c = 1000$):** **CONFIRMED WITH CORRECTED METRIC.** Static Dual-Bucket's rejection on atomic nominal bursts is corrected from $30.0\%$ (hypothetical token split) to **$100.0\%$ (production request-level FRR)**. Under either figure, Static DB is disqualified ($100\% \gg 5\%$). DREB-RCA70 and Token Bucket are the sole eligible policies and are mutually non-dominated. Operational Frontier = `{"rca70", "tb_large"}`.
> 3. **Scope 3 (Composite):** **CONFIRMED.** Multi-tenant contracts requiring both sub-bursts and nominal bursts disqualify Static DB for failing on $c=1000$. Operational Frontier = `{"rca70", "tb_large"}`.

---

## 2. Definitive Operational Eligibility & Pareto Analysis

To ensure complete clarity, **Operational Eligibility** (satisfying availability SLAs) is reported separately from **Pareto Non-Dominance** (optimality among eligible policies).

### 2.1 Operational Eligibility Thresholds
A candidate policy is operationally eligible if and only if:
$$\text{FRR}_{\text{burst}} \le 5.0\%, \quad \text{FRR}_{\text{post}} \le 5.0\%$$
where $\text{FRR}_{\text{burst}}$ is evaluated at request level under the production API contract.

| Policy ID | Scope 1 ($c \le 700$) Burst FRR | Scope 2 ($c = 1000$) Burst FRR | Post-Attack Recovery FRR (W10) | Scope 1 Eligibility | Scope 2 Eligibility | Scope 3 Eligibility | Reason for Any Disqualification |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **`static_db_070c`** | $\mathbf{0.00\%}$ | $\mathbf{100.0\%}$ (atomic) | $\mathbf{0.00\%}$ | **ELIGIBLE** | **DISQUALIFIED** | **DISQUALIFIED** | Rejects 100% of nominal atomic bursts ($c=1000$) |
| **`rca70`** | $\mathbf{0.00\%}$ | $\mathbf{0.00\%}$ | $\mathbf{0.00\%}$ | **ELIGIBLE** | **ELIGIBLE** | **ELIGIBLE** | Satisfies SLA under all scopes via idle credit |
| **`tb_large`** | $\mathbf{0.00\%}$ | $\mathbf{0.00\%}$ | $\mathbf{0.00\%}$ | **ELIGIBLE** | **ELIGIBLE** | **ELIGIBLE** | Satisfies SLA under all scopes via full capacity $C$ |
| **`dreb_v2_default`** | $2.56\%$ | $\mathbf{100.0\%}$ (atomic) | $0.00\%$ | **ELIGIBLE** | **DISQUALIFIED** | **DISQUALIFIED** | Rejects nominal burst exceeding $900$ |
| **`dreb_ac_bm`** | $\mathbf{64.10\%}$ | $\mathbf{100.0\%}$ (atomic) | $\mathbf{60.00\%}$ | **DISQUALIFIED** | **DISQUALIFIED** | **DISQUALIFIED** | Extreme burst starvation on all scopes |

---

## 3. Scope-by-Scope Operational Pareto Frontiers

### 3.1 Scope 1: Constrained Bursts ($c \le 700$)
- **Eligible Set:** `{"static_db_070c", "rca70", "tb_large", "dreb_v2_default"}`
- **Pairwise Dominance:**
  - Static DB vs. Token Bucket: Static DB is tied on FRR ($0\%$), BAR ($100\%$), RecTime ($1.0\,\text{s}$), Credit ($0.0R$), and P50 ($42\,\text{ns}$); Static DB is strictly better on AdvRate ($0.9928R < 0.9978R$ on W09; $0.3599R < 0.7561R$ on Mean) and P99 ($83\,\text{ns} < 125\,\text{ns}$). $\implies$ **Static DB dominates Token Bucket.**
  - Static DB vs. RCA70: Static DB is strictly better on AdvRate, Credit, and Latency. $\implies$ **Static DB dominates RCA70.**
  - Static DB vs. DREB v2: Static DB is strictly better across all six dimensions. $\implies$ **Static DB dominates DREB v2.**
- **Operational Frontier:**
  $$\mathbf{\text{Frontier}_{\text{Scope 1}} = \{\text{Static Dual-Bucket (0.70C)}\}}$$

---

### 3.2 Scope 2: Nominal Capacity Bursts ($c = 1000$)
- **Eligible Set:** `{"rca70", "tb_large"}`
- **Pairwise Dominance:**
  - RCA70 has strictly lower adversarial throughput ($0.3467R$ vs. $0.8000R$ on W08; $0.6342R$ vs. $0.7561R$ on Mean).
  - Token Bucket has strictly lower credit draw ($0.0000R$ vs. $0.0667R$) and lower latency ($125\,\text{ns}$ vs. $208\,\text{ns}$ P99; $42\,\text{ns}$ vs. $125\,\text{ns}$ P50).
  - Neither dominates the other.
- **Operational Frontier:**
  $$\mathbf{\text{Frontier}_{\text{Scope 2}} = \{\text{DREB-RCA70},\ \text{Token Bucket (C, R)}\}}$$

---

### 3.3 Scope 3: Composite Multi-Tenant Workload
- **Eligible Set:** `{"rca70", "tb_large"}`
- Because Static DB is disqualified whenever any client issues a nominal burst of weight $1000$, the composite frontier is identical to Scope 2:
  $$\mathbf{\text{Frontier}_{\text{Scope 3}} = \{\text{DREB-RCA70},\ \text{Token Bucket (C, R)}\}}$$

---

## 4. Separation from Unconstrained Mathematical Frontier

For complete scientific integrity, the **unconstrained mathematical Pareto frontier** (evaluating raw trade-offs without post-hoc SLA disqualification) is maintained separately:

| Mathematical Evaluation Space | Non-Dominated Policies | Mathematical Basis for Non-Dominance |
| :--- | :--- | :--- |
| **Unconstrained Space (Scope 1, $c \le 700$)** | **`{dreb_ac_bm, static_db_070c, tb_large}`** | • **DREB-AC BM:** Lowest adversarial rate on W07 ($0.6955R$) and lowest raw latency ($42\,\text{ns}$ P99).<br>• **Static DB:** Lowest adversarial rate on W08 ($0.0000R$) and lowest burst FRR ($0\%$).<br>• **Token Bucket:** Baseline benchmark reference. |
| **Unconstrained Space (Scope 2, $c = 1000$)** | **`{dreb_ac_bm, rca70, tb_large}`** | • **DREB-AC BM:** Lowest adversarial rate on W07 ($0.6955R$) and lowest latency.<br>• **RCA70:** Lowest adversarial rate among full-burst ($1000$) policies ($0.3467R$).<br>• **Token Bucket:** Zero credit draw ($0.0000R$). |

---

## 5. Master Cross-Report Reconciliation Table

| Dimension | Previous Claim (Phase 5.3 / 5.4) | Phase 5.4.1 Reconstructed Evidence | Status |
| :--- | :--- | :--- | :--- |
| **Nominal Burst Rejection Metric** | Static DB rejects $30.0\%$ tokens ($70\%$ burst acceptance) on $C=1000$. | The production `RateLimiter.allow` API is strictly atomic. A request of `cost=1000` is rejected entirely, producing **$100.0\%$ request-level FRR** and **$100.0\%$ token rejection**. | **Corrected** |
| **Token Bucket Latency Reconciliation** | Token Bucket latency cited as $42\,\text{ns}$ while Static DB was cited as $83\,\text{ns}$. | $42\,\text{ns}$ was Token Bucket's P50 median. Under matched statistics, Static DB ($83\,\text{ns}$) is strictly faster than Token Bucket ($125\,\text{ns}$) at P99, and tied ($42\,\text{ns}$) at P50. | **Corrected** |
| **Scope 1 Operational Dominance** | Static DB claimed to dominate all Scope 1 policies. | Confirmed under complete 6D workload-matched vectors (using W09 $c=500$ as the Scope 1 attack workload) across both P99 and P50 matched latency measures. | **Confirmed** |
| **Scope 2 Operational Frontier** | Operational Frontier = `{DREB-RCA70, Token Bucket}`. | Confirmed. Static DB is disqualified by its $100\%$ atomic request rejection on $c=1000$, leaving RCA70 and Token Bucket. | **Confirmed** |
