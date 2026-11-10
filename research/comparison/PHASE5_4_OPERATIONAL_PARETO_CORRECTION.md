# Phase 5.4 — Operational Pareto Frontier Correction & Workload Scope Sensitivity

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`1fddbe2`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Policies:** DREB-RCA70, Static Dual-Bucket (0.70C), Token Bucket (C, R), DREB-AC BM, DREB v2 Default  
**Audit Predecessor:** [`PHASE5_4_OPERATIONAL_ELIGIBILITY_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/PHASE5_4_OPERATIONAL_ELIGIBILITY_AUDIT.md)  
**Verification Test Suite:** [`Phase54OperationalEligibilityTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase54OperationalEligibilityTest.java)

---

## 1. Executive Summary & Correction Statement

Phase 5.3 reported an Operationally Eligible Frontier consisting of three policies:
$$\text{Reported in Phase 5.3:} \quad \{\text{DREB-RCA70},\ \text{Static Dual-Bucket},\ \text{Token Bucket}\}$$

Following the Phase 5.4 consistency audit, **this three-policy frontier is formally retracted as a hybrid contradiction resulting from incompatible workload scopes**:
1. **Under Scope 1 (Constrained Bursts, $c \le 700$):**
   Static Dual-Bucket achieves $0.00\%$ FRR and is eligible, but **Static Dual-Bucket strictly dominates RCA70 and Token Bucket** across all six dimensions. The operational frontier contains only **$\{\text{Static Dual-Bucket}\}$**.
2. **Under Scope 2 (Elastic Nominal Bursts, $C = 1000$):**
   Static Dual-Bucket rejects $30.0\%$ of nominal burst tokens ($100\%$ of atomic requests), violating the $5.0\%$ FRR threshold by $6\times$. **Static Dual-Bucket is disqualified for SLA non-compliance**. The operational frontier contains only **$\{\text{DREB-RCA70},\ \text{Token Bucket}\}$**.

Under **no single consistent workload scope** can RCA70, Static DB, and Token Bucket simultaneously satisfy the operational SLA and remain mutually non-dominated.

---

## 2. Task C — Reconstruction of the Operational Frontier

The declared operational engineering constraints are strictly preserved:
$$\text{FRR}_{\text{burst}} \le 5.0\%, \quad \text{FRR}_{\text{post}} \le 5.0\%$$

### 2.1 Scope 1: Constrained Bursts ($c \le 700$, Phase 5 Empirical Benchmark)
In this scope, applications require bursts no larger than $c = 700$ (as represented in benchmark workloads W02 and W10).

#### Policy Eligibility:
- `rca70`: $\text{FRR}_{\text{burst}} = 0.00\%$, $\text{FRR}_{\text{post}} = 0.00\%$. **ELIGIBLE**.
- `static_db_070c`: $\text{FRR}_{\text{burst}} = 0.00\%$, $\text{FRR}_{\text{post}} = 0.00\%$. **ELIGIBLE**.
- `tb_large`: $\text{FRR}_{\text{burst}} = 0.00\%$, $\text{FRR}_{\text{post}} = 0.00\%$. **ELIGIBLE**.
- `dreb_v2_default`: $\text{FRR}_{\text{burst}} = 2.56\%$, $\text{FRR}_{\text{post}} = 0.00\%$. **ELIGIBLE**.
- `dreb_ac_bm`: $\text{FRR}_{\text{burst}} = 64.10\%$, $\text{FRR}_{\text{post}} = 60.00\%$. **DISQUALIFIED** (extreme false rejection).

#### Pairwise Dominance Among Eligible Policies:
| Policy | $\text{FRR}_{\text{burst}}$ | $\text{BAR}_{\text{burst}}$ | $\text{FRR}_{\text{post}}$ | Adv Rate (W08) | Credit Rate (W05) | P99 Latency | Dominance Outcome |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **`static_db_070c`** | $\mathbf{0.00\%}$ | $\mathbf{100.0\%}$ | $\mathbf{0.00\%}$ | $\mathbf{0.0000R}$ | $\mathbf{0.0000R}$ | $\mathbf{83\,\text{ns}}$ | **Dominates all eligible policies** |
| `rca70` | $0.00\%$ | $100.0\%$ | $0.00\%$ | $0.3467R$ | $0.0667R$ | $208\,\text{ns}$ | Dominated by Static DB |
| `tb_large` | $0.00\%$ | $100.0\%$ | $0.00\%$ | $0.8000R$ | $0.0000R$ | $125\,\text{ns}$ | Dominated by Static DB |
| `dreb_v2_default`| $2.56\%$ | $97.4\%$ | $0.00\%$ | $0.7067R$ | $0.3214R$ | $125\,\text{ns}$ | Dominated by Static DB |

$$\mathbf{\text{Operational Frontier}_{\text{Scope 1 (c}\le\text{700)}} = \{\text{Static Dual-Bucket (0.70C)}\}}$$

---

### 2.2 Scope 2: Elastic Contracted Bursts ($C = 1000$, Nominal Capacity)
In this scope, the SLA guarantees that after idleness, a client may burst up to the nominal contracted limit $C = 1000$.

#### Policy Eligibility:
- `rca70`: $\text{FRR}_{1000} = 0.00\%$, $\text{FRR}_{\text{post}} = 0.00\%$. **ELIGIBLE**.
- `tb_large`: $\text{FRR}_{1000} = 0.00\%$, $\text{FRR}_{\text{post}} = 0.00\%$. **ELIGIBLE**.
- `static_db_070c`: $\text{FRR}_{1000} = \mathbf{30.00\%} > 5.0\%$. **DISQUALIFIED** (violates burst availability SLA).
- `dreb_ac_bm`: $\text{FRR}_{1000} = \mathbf{70.00\%} > 5.0\%$. **DISQUALIFIED**.
- `dreb_v2_default`: $\text{FRR}_{1000} = \mathbf{10.00\%} > 5.0\%$. **DISQUALIFIED**.

#### Pairwise Dominance Between Eligible Policies:
Only two policies satisfy the operational eligibility threshold:
- `rca70`: Admitted adv rate = $0.3467R$, credit rate = $0.0667R$, P99 latency = $208\,\text{ns}$.
- `tb_large`: Admitted adv rate = $0.8000R$, credit rate = $0.0000R$, P99 latency = $125\,\text{ns}$.

`rca70` achieves strictly lower adversarial throughput ($0.3467R < 0.8000R$, a $56.7\%$ reduction).  
`tb_large` achieves lower decision latency ($125\,\text{ns} < 208\,\text{ns}$) and zero credit draw ($0.0000R < 0.0667R$).  
Neither policy dominates the other.

$$\mathbf{\text{Operational Frontier}_{\text{Scope 2 (elastic } C=1000\text{)}} = \{\text{DREB-RCA70},\ \text{Token Bucket (C, R)}\}}$$

---

## 3. Task D — Workload Scope Sensitivity Matrix

The following matrix synthesizes the sensitivity of policy eligibility and Pareto dominance across different workload definitions:

| Evaluation Scope | Evaluated Burst Scale | Eligible Policies ($\text{FRR} \le 5\%$) | Disqualified Policies | Operational Pareto Frontier | Key Engineering Takeaway |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Scope 1: Constrained Bursts** | $c \le 700$ (W02, W10) | `static_db_070c`, `rca70`, `tb_large`, `dreb_v2` | `dreb_ac_bm` ($64.1\%$ FRR) | **`{static_db_070c}`** | If an application never bursts above $0.70C$, Static DB is optimal and eliminates credit farming completely. |
| **Scope 2: Elastic Bursts** | $C = 1000$ (Nominal Capacity) | `rca70`, `tb_large` | `static_db_070c` ($30\%$ FRR), `dreb_ac_bm` ($70\%$ FRR), `dreb_v2` ($10\%$ FRR) | **`{rca70, tb_large}`** | If the contract promises full burst elasticity up to $C$, Static DB is unusable; RCA70 is the sole policy offering strong adversarial suppression while preserving $100\%$ burst capacity. |
| **Scope 3: Combined / Dual-Mode** | Both $c \le 700$ and $C = 1000$ | `rca70`, `tb_large` | `static_db_070c`, `dreb_ac_bm`, `dreb_v2` | **`{rca70, tb_large}`** | Any multi-tenant environment containing both small and large burst clients excludes Static DB. |
| **Unconstrained Mathematical Space** | All 10 workloads (no SLA filter) | All 5 policies | None | **`{dreb_ac_bm, static_db_070c, tb_large}`** (or with elastic burst: **`{dreb_ac_bm, rca70, tb_large}`**) | DREB-AC BM survives mathematically because it achieves the lowest adversarial rate ($0.12R$ AIMD, $0.42R$ mean). |

---

## 4. Task E — Cross-Report Reconciliation Table

| # | Previous Claim in Phase 5.3 | Reconstructed Result in Phase 5.4 | Correct Scientific Interpretation | Status |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **Operational Frontier Composition**<br>*(Phase 5.3 Report, §3.4)*<br>"The Operationally Eligible Frontier = {DREB-RCA70, Static Dual-Bucket, Token Bucket}." | Under Scope 1 ($c \le 700$), Static DB dominates RCA70 and TB, yielding `{Static DB}`. Under Scope 2 ($C=1000$), Static DB incurs $30\%$ FRR and is disqualified, yielding `{RCA70, Token Bucket}`. | The 3-policy frontier was a hybrid contradiction resulting from evaluating eligibility under $c \le 700$ while evaluating non-dominance under $C=1000$. Operational frontiers must be reported with explicit workload scope. | **Corrected** |
| **2** | **Static DB Operational Eligibility**<br>*(Phase 5.3 Report, §1, §3.4)*<br>"Static Dual-Bucket satisfies the operational eligibility threshold $\text{FRR}_{\text{burst}} \le 5\%$." | On nominal contracted bursts ($C=1000$), Static DB admits 700 tokens and rejects 300 tokens, giving $\text{FRR}_{\text{burst}} = 30.0\%$ (or $100\%$ for atomic requests). | Static DB satisfies eligibility **only** when applications are restricted to $c \le 700$. It violates the operational availability SLA by $6\times$ on nominal $C=1000$ bursts. | **Corrected** |
| **3** | **Static DB Dominance over RCA70**<br>*(Phase 5.3 Report, §3.3)*<br>"Static Dual-Bucket does not dominate RCA70." | In the Phase 5 empirical benchmark suite ($c \le 700$, W02), Static DB strictly dominates RCA70 across all six dimensions. | Static DB does not dominate RCA70 **if and only if** nominal elastic burst capacity ($C=1000$) is included as an objective. Under that same condition, Static DB is disqualified by the operational SLA filter. | **Corrected** |

---

## 5. Architectural Implications for DREB-RCA70

This audit clarifies the exact engineering justification for DREB-RCA70:
- **If an API gateway contracts burst capacity strictly capped at $0.70C$:**  
  There is no reason to deploy dynamic credit mechanics. A simple static dual-bucket (`Static DB 0.70C`) achieves zero false rejections, zero adversarial credit leakage, and lower CPU overhead ($83\,\text{ns}$).
- **If an API gateway contracts full burst capacity $C = 1000$ after idle periods:**  
  Static DB is disqualified ($30\%$ rejection rate). Token Bucket accommodates bursts but admits heavy adversarial traffic ($0.80R - 1.00R$). **DREB-RCA70 is the sole viable architecture**, accommodating full nominal bursts ($100\%$ BAR, $0\%$ FRR) while clamping adversarial farming to $\le 0.07R$ and total adversarial rate to $0.35R$.
