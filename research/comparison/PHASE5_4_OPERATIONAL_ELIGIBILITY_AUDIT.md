# Phase 5.4 — Operational Pareto Eligibility & Workload Consistency Audit

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`1fddbe2`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Policies:** DREB-RCA70, Static Dual-Bucket (0.70C), Token Bucket (C, R), DREB-AC BM, DREB v2 Default  
**Raw Source Dataset:** [`phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv) (1,501 rows, strictly unchanged)  
**Verification Test Suite:** [`Phase54OperationalEligibilityTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase54OperationalEligibilityTest.java)

---

## 1. Executive Summary: The Static DB Contradiction

In Phase 5.3, the research reports asserted the following simultaneous claims:
1. **Operational SLA Thresholds:** A candidate policy is operationally eligible if and only if its false rejection rate on legitimate bursts and post-attack recovery does not exceed 5%:
   $$\text{FRR}_{\text{burst}} \le 5.0\%, \quad \text{FRR}_{\text{post}} \le 5.0\%$$
2. **Burst Elasticity Qualification:** Static Dual-Bucket ($B_{\max} = 700$, $I_{\max} = 0$) accommodates only 700 tokens of a nominal $C=1000$ post-idle burst, resulting in:
   $$\text{BAR}_{\text{burst}} = 70.0\% \implies \text{FRR}_{\text{burst}} = 30.0\%$$
3. **Reported Operational Frontier:** The operationally eligible Pareto frontier was reported as:
   $$\text{Operational Frontier}_{\text{Phase 5.3}} = \{\text{DREB-RCA70},\ \text{Static Dual-Bucket},\ \text{Token Bucket}\}$$

### The Core Inconsistency
As identified in the independent audit, **these three statements are mathematically incompatible under any single consistent workload scope**:
- If nominal $C=1000$ bursts are part of the operational burst workload, Static DB suffers a **$30.0\%$ false rejection rate** (or $100\%$ for atomic requests). This violates $\text{FRR}_{\text{burst}} \le 5.0\%$ by a factor of $6\times$, rendering **Static DB unequivocally INELIGIBLE**.
- If nominal $C=1000$ bursts are excluded (evaluating only the Phase 5 empirical benchmark where bursts are restricted to $c \le 700$), Static DB achieves $\text{FRR}_{\text{burst}} = 0.00\%$ and is eligible, but on that exact same workload **Static DB strictly dominates RCA70 and Token Bucket** across all six dimensions.
- Phase 5.3 committed a **hybrid dual-standard fallacy**: it used $C=1000$ to prevent Static DB from dominating RCA70, while simultaneously evaluating operational eligibility on $c \le 700$ to allow Static DB to pass the $5\%$ FRR SLA.

This audit reconstructs the raw empirical workload data, validates limiter behavior under nominal bursts, and resolves the contradiction.

---

## 2. Task A — Empirical Reconstruction of Legitimate Metrics from Raw Dataset

An audit of [`phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv) across all 30 deterministic seeds (5000–5029) establishes the exact composition of legitimate traffic across all 10 evaluated workloads.

### 2.1 Workload Taxonomy for Legitimate Traffic
Of the 10 workloads executed in Phase 5:
- **Workloads with Zero Legitimate Traffic:** W05 (refill farming), W06 (white-box oracle), W07 (black-box probe), W08 (AIMD closed-loop), and W09 (multi-worker collision) contain $100\%$ adversarial traffic ($\text{offered\_legit} = 0$).
- **W01 (Benign Steady):** Continuous Poisson stream ($0.98R$, step $0.05\,\text{s}$). Offered $\approx 58,817\,\text{tokens}$ ($\approx 1,176\,\text{requests}$). All 5 policies admitted $100\%$ of traffic ($\text{FRR} = 0.00\%$).
- **W03 (Sustained Overload):** Continuous uncontracted overload ($1.50R$, offering $90,000\,\text{tokens}$). All policies correctly admitted $\approx 61,275\,\text{tokens}$ ($\approx 1.02R$), shedding excess traffic ($\text{FRR} \approx 31.9\%$).
- **W04 (Mixed Competing):** Poisson legitimate stream (cost 50, gap $0.2\,\text{s}$) competing with periodic adversarial bursts (cost 1000, gap $1.5\,\text{s}$). Offered $\approx 15,168\,\text{tokens}$. Legitimate FRR: Static DB $0.00\%$, RCA70 $0.22\%$, DREB-AC $0.28\%$, DREB v2 $0.81\%$, Token Bucket $2.91\%$.
- **W02 (Legitimate ON/OFF Burst):** The primary benchmark workload for burst evaluation.
- **W10 (Post-Attack Recovery):** The primary benchmark workload for post-attack recovery.

### 2.2 Reconstructed Performance on W02 (Compliant Burst $c = 700$)
Workload W02 delivers 39 periodic bursts of cost $c = 700$ spaced by $1.5\,\text{s}$ idle gaps (offered total: $27,300\,\text{tokens}$).

| Policy ID | Requests Offered | Requests Admitted | Requests Denied | Tokens Offered | Tokens Admitted | Tokens Denied | Empirical $\text{FRR}_{\text{burst}}$ | Empirical $\text{BAR}_{\text{burst}}$ |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`rca70`** | 39 | 39 | 0 | 27,300 | 27,300 | 0 | **$0.00\%$** | **$100.00\%$** |
| **`static_db_070c`** | 39 | 39 | 0 | 27,300 | 27,300 | 0 | **$0.00\%$** | **$100.00\%$** |
| **`tb_large`** | 39 | 39 | 0 | 27,300 | 27,300 | 0 | **$0.00\%$** | **$100.00\%$** |
| **`dreb_ac_bm`** | 39 | 14 | 25 | 27,300 | 9,800 | 17,500 | **$64.10\%$** | **$35.90\%$** |
| **`dreb_v2_default`** | 39 | 38 | 1 | 27,300 | 26,600 | 700 | **$2.56\%$** | **$97.44\%$** |

### 2.3 Reconstructed Performance on W10 (Post-Attack Recovery $[30\,\text{s}, 60\,\text{s}]$)
Workload W10 subjects the limiter to heavy adversarial saturation ($[0, 30\,\text{s}]$), followed by 20 legitimate bursts of cost $c = 700$ spaced by $1.5\,\text{s}$ idle gaps in $[31\,\text{s}, 60\,\text{s}]$ (offered total: $14,000\,\text{tokens}$).

| Policy ID | Requests Offered | Requests Admitted | Requests Denied | Tokens Offered | Tokens Admitted | Tokens Denied | Empirical $\text{FRR}_{\text{post}}$ | Empirical $\text{BAR}_{\text{post}}$ | Recovery Latency |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`rca70`** | 20 | 20 | 0 | 14,000 | 14,000 | 0 | **$0.00\%$** | **$100.00\%$** | $1.00\,\text{s}$ |
| **`static_db_070c`** | 20 | 20 | 0 | 14,000 | 14,000 | 0 | **$0.00\%$** | **$100.00\%$** | $1.00\,\text{s}$ |
| **`tb_large`** | 20 | 20 | 0 | 14,000 | 14,000 | 0 | **$0.00\%$** | **$100.00\%$** | $1.00\,\text{s}$ |
| **`dreb_ac_bm`** | 20 | 8 | 12 | 14,000 | 5,600 | 8,400 | **$60.00\%$** | **$40.00\%$** | $1.00\,\text{s}$ |
| **`dreb_v2_default`** | 20 | 20 | 0 | 14,000 | 14,000 | 0 | **$0.00\%$** | **$100.00\%$** | $1.00\,\text{s}$ |

### 2.4 Origin of the $C=1000$ Burst Scenario
Notice that in `phase5_baseline_comparison_raw.csv`, **all legitimate bursts in W02 and W10 were generated with $c = 700$**.
The $C=1000$ post-idle burst scenario was **not** an executed workload in the Phase 5 empirical matrix; it was introduced analytically in Phase 5.3 (§5.1) to demonstrate that Static DB's empirical parity with RCA70 on W02 was an artifact of capping the benchmark burst size to $B_{\max}^{\text{static}} = 700$.

---

## 3. Task B — Mathematical & Empirical Verification of the Static DB Contradiction

### 3.1 Limiter Decision Mechanics on Nominal Burst ($C = 1000$)
Under contract parameters, the system sustained rate is $R = 1000\,\text{tokens/s}$ and nominal burst capacity is $C = 1000\,\text{tokens}$.
When a fully rested client attempts a nominal burst of $C = 1000$ tokens:

1. **Static Dual-Bucket (`static_db_070c`):**
   - Burst bucket capacity is hardcoded to $B_{\max} = 700$. Zero idle credit ($I_{\max} = 0$).
   - **Atomic Batch Request ($1$ request of cost $1000$):**
     `effectiveBurst` $= 700 < 1000$. The limiter returns `Decision.DENY`.
     $$\text{Admitted} = 0, \quad \text{Denied} = 1 \implies \mathbf{\text{FRR}_{\text{burst}} = 100.0\%}$$
   - **Streaming / Token Request ($1000$ requests of cost $1$):**
     The first 700 tokens deplete $B$. The remaining 300 tokens are denied.
     $$\text{Admitted} = 700, \quad \text{Denied} = 300 \implies \mathbf{\text{FRR}_{\text{burst}} = 30.0\%}, \quad \mathbf{\text{BAR}_{\text{burst}} = 70.0\%}$$
   - Under either atomic or streaming semantics, $\text{FRR}_{\text{burst}} \ge 30.0\% \gg 5.0\%$.

2. **DREB-RCA70 (`rca70`):**
   - Base burst $B_{\max} = 700$. Idle credit $I_{\max} = 450$ accrued at $1.5R$ during idleness ($\ge 0.30\,\text{s}$).
   - Allowable burst: $B_{\max} + I_{\max}/1.5 = 700 + 300 = 1000$.
   - **Atomic Batch Request:** Admitted via base burst + idle credit.
   - **Streaming Token Request:** Admitted $1000$ tokens, denied $0$.
   $$\mathbf{\text{FRR}_{\text{burst}} = 0.00\%}, \quad \mathbf{\text{BAR}_{\text{burst}} = 100.00\%}$$

3. **Token Bucket (`tb_large`):**
   - Bucket capacity $C = 1000$. Full bucket holds 1000 tokens.
   $$\mathbf{\text{FRR}_{\text{burst}} = 0.00\%}, \quad \mathbf{\text{BAR}_{\text{burst}} = 100.00\%}$$

4. **DREB-AC BM (`dreb_ac_bm`):**
   - Base burst $B_{\max} = 300$. Admitted 300, denied 700.
   $$\mathbf{\text{FRR}_{\text{burst}} = 70.0\%}, \quad \mathbf{\text{BAR}_{\text{burst}} = 30.0\%}$$

5. **DREB v2 Default (`dreb_v2_default`):**
   - Base burst $B_{\max} = 300$, $I_{\max} = 900$. Max allowable burst $300 + 600 = 900 < 1000$. Admitted 900, denied 100.
   $$\mathbf{\text{FRR}_{\text{burst}} = 10.0\%}, \quad \mathbf{\text{BAR}_{\text{burst}} = 90.0\%}$$

### 3.2 Direct Comparison with Operational SLA Criterion
The declared operational criterion is:
$$\text{FRR}_{\text{burst}} \le 5.0\%$$

| Policy ID | Reconstructed Burst FRR ($c=700$, W02) | Reconstructed Burst FRR ($C=1000$, Nominal) | Reconstructed Post-Attack FRR (W10) | Satisfies SLA under $c=700$? | Satisfies SLA under $C=1000$? |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **`rca70`** | $0.00\%$ | $0.00\%$ | $0.00\%$ | **YES** | **YES** |
| **`static_db_070c`** | $0.00\%$ | **$30.00\%$** (or $100\%$) | $0.00\%$ | **YES** | **NO ($30\% > 5\%$)** |
| **`tb_large`** | $0.00\%$ | $0.00\%$ | $0.00\%$ | **YES** | **YES** |
| **`dreb_ac_bm`** | $64.10\%$ | $70.00\%$ | $60.00\%$ | **NO** | **NO** |
| **`dreb_v2_default`** | $2.56\%$ | $10.00\%$ | $0.00\%$ | **YES** | **NO ($10\% > 5\%$)** |

### 3.3 Proof of the Contradiction
- If $C=1000$ is included in the operational SLA calculation:
  Static DB incurs $\text{FRR}_{\text{burst}} = 30.0\% > 5.0\%$. Static DB **fails the operational eligibility threshold** and cannot be included on the operational frontier.
- If $C=1000$ is excluded:
  Static DB incurs $\text{FRR}_{\text{burst}} = 0.00\% \le 5.0\%$ and is eligible, but on that exact same workload Static DB **dominates RCA70**.
- Therefore, the claim in Phase 5.3 that `{DREB-RCA70, Static Dual-Bucket, Token Bucket}` forms the operational frontier is a **hybrid contradiction**. It cannot be sustained under any unified, mathematically consistent workload scope.
