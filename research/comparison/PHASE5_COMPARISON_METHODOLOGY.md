# Phase 5 — Comparison Methodology and Fairness Audit

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`9ecbfc9`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Policies:** DREB-RCA70, DREB-AC BM, DREB v2 Default, Static Dual-Bucket (0.70C), Token Bucket (C, R)  
**Harness Source:** [`Phase5BaselineComparisonTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase5BaselineComparisonTest.java)  
**Raw Dataset:** [`phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv)

---

## 1. Executive Objective

Phase 5 conducts an independent, scientifically defensible comparative evaluation of **DREB-RCA70** against four established baseline policies under strictly identical, paired experimental conditions.

The objective is to test whether RCA70 provides a demonstrable Pareto-optimal operational advantage when evaluated across:
1. Benign legitimate service quality (false rejection rate, burst tolerance, steady admission).
2. Adversarial credit-farming and adaptive evasion suppression.
3. Guarantee G1 cumulative rate envelope compliance.
4. Operational runtime overhead (decision latency, memory footprint, implementation complexity).

---

## 2. Experimental Fairness Audit

To prevent experimental bias and ensure scientific validity, all comparators were audited against the following fairness criteria before data collection:

### 2.1 Paired Arrival Schedules
All policies receive **strictly identical arrival event streams** $(\{t_i, c_i, \text{isAdversary}\})$ generated from identical pseudo-random seeds ($N=30$ seeds, $5000 \dots 5029$). The sequence of arrival intervals and request costs presented to policy $P_a$ is identical to that presented to policy $P_b$.

### 2.2 Initial State Uniformity
Every simulation run initializes from the policy's canonical cold-start state at $t = 0.0\,\text{s}$:
- For DREB policies: $B_0 = B_{\max}$, $S_0 = S_{\max}$, $I_0 = 0$, $K_0 = 0$.
- For Token Bucket: $Tokens_0 = C = 1000$.

### 2.3 Transparent Parameter Disclosure
No configuration values are tuned per workload scenario. All policies operate with fixed parameters throughout the evaluation:

| Policy ID | Policy Name | Burst Allocation ($\alpha$) | $B_{\max}$ | $R_{\text{burst}}$ | $S_{\max}$ | Idle Policy | Idle Parameters |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`rca70`** | **DREB-RCA70** | $0.70$ | $700$ | $1666.67$ | $1285.71$ | Adaptive Ceiling | $\text{baseCap}=450, \text{baseDecay}=1.5, \beta=0.75, \lambda_{\text{rec}}=0.05$ |
| **`dreb_ac_bm`** | DREB-AC BM | $0.30$ | $428.57$ | $1666.67$ | $1285.71$ | Adaptive Ceiling | $\text{baseCap}=857.14, \text{baseDecay}=1.5, \beta=0.75, \lambda_{\text{rec}}=0.05$ |
| **`dreb_v2_default`** | DREB v2 Default | $0.30$ | $428.57$ | $1666.67$ | $1285.71$ | Adaptive Idle | Heuristic $K$ tracking, $\text{baseCap}=857.14, \text{baseDecay}=1.0$ |
| **`static_db_070c`** | Static DB (0.70C)| $0.70$ | $700$ | $1666.67$ | $1285.71$ | Linear (Disabled) | $\text{idleRefill}=0, \text{idleCap}=0$ (No idle credit) |
| **`tb_large`** | Token Bucket | $1.00$ | $1000$ | $1000.00$ | $1000.00$ | Classical TB | $C=1000, R=1000$ |

### 2.4 Explicit Boundaries of Fairness
1. **White-Box Oracle Workload (W06):** In W06, the attacker reads the limiter's internal registers. Because each policy has different internal state representations (e.g. Token Bucket has a single bucket $C$, while DREB has dual buckets $B, S$ and idle credit $I$), each policy's oracle queries its own native state to determine maximum instantaneously admissible tokens:
   - For DREB: $c^*(t) = \max\left(1, \lfloor \min(S(t), B(t) + I_{\text{eff}}(t)/1.5) \rfloor\right)$.
   - For Token Bucket: $c^*(t) = \max(1, \lfloor Tokens(t) \rfloor)$.
   This tests each policy's resistance to optimal extraction under complete information.
2. **Post-Attack Recovery Workload (W10):** The legitimate client requests compliant bursts of $c=700$. For policies with $B_{\max} \ge 700$ (RCA70, Static DB, Token Bucket), this burst fits within the base burst envelope. For policies with $B_{\max} = 428.57$ (DREB-AC, DREB v2), admitting $c=700$ requires drawing idle credit. This architectural difference is the primary research question under evaluation.

---

## 3. Workload Suite Specifications

The benchmark suite defines 10 representative workloads covering the full operational space:

1. **`W01_benign_steady`:** Legitimate Poisson stream offering $0.98R$ ($980\,\text{tokens/s}$) in small steps ($\Delta t = 50\,\text{ms}$). Evaluates nominal steady-state admission and baseline false rejection.
2. **`W02_legitimate_on_off_burst`:** Compliant bursty client requesting $c=700$ tokens every $1.50\,\text{s}$ (inter-burst idle gap of $1.50\,\text{s}$). Evaluates burst accommodation without background load.
3. **`W03_sustained_overload`:** Continuous overload stream offering $1.50R$ ($1500\,\text{tokens/s}$) via $c=15$ requests every $10\,\text{ms}$. Evaluates sustained rate clamping and G1 envelope enforcement.
4. **`W04_mixed_competing`:** Background legitimate Poisson stream ($c=50, \bar{g}=0.20\,\text{s}$) concurrent with periodic adversarial bursts ($c=1000$ every $1.50\,\text{s}$). Evaluates collateral damage during active attacks.
5. **`W05_timing_farming`:** Adversary sending bursts of $c=750$ every $0.470\,\text{s}$ (immediately after burst refill $t_{\text{fill}} = 0.420\,\text{s}$). Evaluates suppression of refill boundary micro-farming.
6. **`W06_white_box_oracle`:** State-aware adversary introspecting internal capacity every $0.50\,\text{s}$. Evaluates worst-case information extraction.
7. **`W07_black_box_probe`:** Adaptive adversary employing Multiplicative-Increase / Multiplicative-Decrease (MIMD) search based on HTTP $\{200, 429\}$ responses every $0.50\,\text{s}$.
8. **`W08_aimd_adaptive`:** Adaptive adversary requesting $c=800$ while modulating waiting intervals via AIMD.
9. **`W09_multi_worker_collision`:** 4 uncoordinated concurrent workers issuing bursts of $c=500$ at jittered intervals of $1.0\,\text{s} \pm 0.1\,\text{s}$.
10. **`W10_post_attack_recovery`:** Adversary active during $[0, 30\,\text{s}]$ ($c=1000$ every $0.720\,\text{s}$), stopping completely at $t=30.0\,\text{s}$. Legitimate client issues compliant bursts ($c=700$) every $1.50\,\text{s}$ during $[30\,\text{s}, 60\,\text{s}]$. Evaluates post-attack recovery latency and collateral starvation.

---

## 4. Metric Definitions and Mathematical Formulations

All simulations run over duration $T = 60.0\,\text{s}$ with contracted rate $R = 1000\,\text{tokens/s}$.

### 4.1 Legitimate Workload Metrics
- **False Rejection Rate ($\text{FRR}_{\text{legit}}$):**
  $$\text{FRR}_{\text{legit}} = \frac{\sum_{i \in \mathcal{R}_{\text{legit}}} c_i - \sum_{i \in \mathcal{A}_{\text{legit}}} c_i}{\sum_{i \in \mathcal{R}_{\text{legit}}} c_i} \times 100\%$$
- **Burst Acceptance Rate ($\text{BAR}$):**
  $$\text{BAR} = \frac{|\mathcal{A}_{\text{burst}}| record}{|\mathcal{R}_{\text{burst}}| record} \times 100\% \quad \text{for requests with } c_i \ge 700$$
- **Post-Attack Recovery Latency ($\tau_{\text{rec}}$):**
  $$\tau_{\text{rec}} = \min \{ t_i - 30.0 \mid t_i \ge 30.0 \land i \in \mathcal{A}_{\text{legit}} \}$$

### 4.2 Adversarial Workload Metrics
- **Normalized Adversarial Rate ($r_{\text{adv}}$):**
  $$r_{\text{adv}} = \frac{\sum_{i \in \mathcal{A}_{\text{adv}}} c_i}{R \cdot T}$$
- **Normalized Credit-Funded Rate ($r_{\text{cred}}$):**
  $$r_{\text{cred}} = \frac{\text{Total Idle Credit Spent}}{R \cdot T}$$
- **Terminal Abuse Penalty ($K_T$):**
  $$K_T = K(T)$$

### 4.3 Guarantee G1 Compliance Metrics
- **Cumulative Admitted Tokens ($A(T)$):**
  $$A(T) = \sum_{i \in \mathcal{A}} c_i$$
- **G1 Rate Envelope ($E_{\text{G1}}(T)$):**
  $$E_{\text{G1}}(T) = S_{\max} + R \cdot T$$
- **G1 Envelope Slack ($Slack(T)$):**
  $$Slack(T) = E_{\text{G1}}(T) - A(T)$$
- **G1 Envelope Violation ($Viol(T)$):**
  $$Viol(T) = \max(0, A(T) - E_{\text{G1}}(T))$$
- **Finite-Window Initial Burst Contribution ($r_{\text{init}}$):**
  $$r_{\text{init}} = \frac{S_{\max}}{R \cdot T}$$

### 4.4 Operational Profiling Metrics
- **Decision Latency (P50, P95, P99):**
  Measured via steady microbenchmarking ($20,000$ warmup iterations followed by $50,000$ timed single-token admissions) using high-resolution hardware timers (`System.nanoTime()`).

---

## 5. Execution and Seed Manifest

The benchmark is executed via Maven:
```bash
mvn test -Dtest=Phase5BaselineComparisonTest
```
- **Seed Manifest:** 30 seeds, starting at $5000$ and ending at $5029$.
- **Generated Dataset:** `research/comparison/phase5_baseline_comparison_raw.csv` (1,501 rows: 1 header + 1,500 data records).
