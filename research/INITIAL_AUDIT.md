# DREB Research Project: Initial Systematic Audit Report (Phase 0)

**Date:** 2026-11-10  
**Git Branch:** `research/systematic-improvement`  
**Base Commit:** `53a5c9e` (`v1.0.2-phase1` tag)  
**Author:** Senior Research Engineer & Systems Audit Team  
**Scope:** Canonical audit of `DREB-RateLimiter`, `DREB-Comparator`, and the research manuscript (`DREB_Conference_Paper.docx` / `DREB.docx`).

---

## 1. Executive Summary

This audit establishes the empirical, algorithmic, and architectural baseline of the Dual-Rate Elastic Bucket (DREB) project prior to executing systematic research improvements.

The project evaluates DREB—an idle-conditioned burst-limiting algorithm designed to award extra instantaneous burst capacity to clients demonstrating prior idleness, while enforcing strict long-run sustained rate caps. The existing evaluation includes both an algorithmic library (`DREB-RateLimiter`), an empirical simulation and benchmarking framework (`DREB-Comparator`), and a conference manuscript (*"Idle-Conditioned Burst Limiting: An Empirical Study of the Dual-Rate Elastic Bucket (DREB), with a Corrected Specification"*).

Crucially, direct verification confirms that the project's own manuscript is an empirical critique rather than an uncritical advocacy piece. It explicitly reports that **DREB records 11 wins, 20 losses, 5 ties, and 4 trade-offs against traditional baselines**, and is **dominated by a simpler static dual-bucket limiter in 29 of 50 evaluated scenarios**.

This audit identifies the structural causes of these outcomes, categorizes all verified shortcomings, and provides an actionable, scientifically sound execution roadmap across the remaining project phases.

---

## 2. Existing System Architecture

### 2.1 Repository Structure & Inter-Dependencies
The project is divided into two GitHub repositories:
1. **`DREB-RateLimiter` (`com.dreb:dreb-ratelimiter`)**:
   - Zero-dependency Java 17+ rate-limiting library.
   - Core engine providing in-memory and Redis-backed state stores.
   - Public facade: `Dreb.builder()` (v1) and `Dreb.builderV2()` (v2).
2. **`DREB-Comparator` (`com.ratelimiter:rate-limiter-benchmark`)**:
   - Discrete-event simulation framework and JMH microbenchmark suite.
   - Evaluates DREB against 7 baseline algorithms across synthetic workloads.
   - Pinned dependency: `com.github.BhagatSingh23:dreb-ratelimiter:v1.0.2-phase1`.

### 2.2 Core Components (`DREB-RateLimiter`)
- **`AbstractRateLimiter`**: Template method governing the rate-limiting lifecycle (`allow(clientId, cost)`):
  1. Input validation ($cost > 0$, finite timestamps).
  2. Timestamp monotonicity clamping ($\Delta t = \max(0, now - lastRefillTs)$, non-regressive $lastRefillTs$).
  3. Dispatch to atomic storage backend (`StateStore.supportsAtomicAllow()`) or in-memory synchronized block.
- **`DREBLimiter` (v1)**: Dual-rate limiter pairing a burst bucket, sustained bucket, and linear idle-credit policy (`LinearIdleCreditPolicy`).
- **`DREBLimiterV2` (v2)**: Extends v1 with an adaptive abuse score (`AdaptiveIdleCreditPolicy`) that escalates credit debit penalties and sustained cost for repeated credit drawers.
- **`TokenBucketLimiter`**: Baseline Token Bucket implementation maintaining identical state fields (`burstTokens`, `sustainedTokens = burstTokens`) to ensure parity across in-memory and Redis backends.
- **`ClientState`**: POJO holding per-client mutable state:
  $$\mathbf{s} = \langle b, s, i, t_{\text{last}}, a \rangle = \langle \text{burstTokens}, \text{sustainedTokens}, \text{idleCredit}, \text{lastRefillTs}, \text{abuseScore} \rangle$$
- **`InMemoryStateStore`**: ConcurrentMap-backed store with per-client synchronization locks.
- **`RedisStateStore`**: Distributed store executing an all-in-one atomic Lua script (`DREB_ALLOW_LUA`) supporting key TTLs, full 64-bit IEEE-754 precision (`%.17g`), and abuse score persistence.

---

## 3. Current Algorithm Specification & Mathematical Guarantees

### 3.1 State and Configuration Parameters
| Parameter | Symbol | Constraints | Role / Description |
|---|:---:|:---:|---|
| Sustained Capacity | $S_{\max}$ | $> 0$ | Long-run cumulative token ceiling |
| Sustained Refill Rate | $S_{\text{rate}}$ | $> 0$ | Contracted sustained admission rate ($R$) |
| Burst Capacity | $B_{\max}$ | $> 0$ | Fast-bucket instantaneous capacity |
| Burst Refill Rate | $B_{\text{rate}}$ | $> 0$ | Fast-bucket refill rate |
| Idle Credit Cap | $\text{idle\_cap}$ | $\ge 0$ | Maximum accrued idle bonus tokens |
| Idle Accrual Gain | $\text{idle\_gain}$ | $\ge 0$ | Accrual rate per second of burst-full idleness |
| Idle Decay Multiplier | $\text{decay}$ / $\text{baseDecay}$ | $> 1$ | Debit penalty factor when drawing idle credit |
| Abuse Gain (v2) | $\alpha$ | $\ge 0$ | Scaling factor of abuse score onto effective decay |
| Abuse Increment (v2) | $\delta_a$ | $\ge 0$ | Increment added to abuse score per unit of drawn credit |
| Abuse Decay Rate (v2) | $\lambda_a$ | $\ge 0$ | Linear dissipation rate of abuse score per second |
| Sustained Cost Gain (v2) | $\kappa$ | $\ge 0$ | Penalty scaling factor applied to sustained bucket cost |

### 3.2 Parameter Derivation Mappings
Given nominal capacity $C$ and contracted sustained rate $R$:
1. **Proportional Mapping (v1 default)**:
   $$B_{\max} = 0.2 C, \quad B_{\text{rate}} = 0.2 R, \quad S_{\max} = C, \quad S_{\text{rate}} = R, \quad \text{idle\_cap} = 0.6 C, \quad \text{idle\_gain} = 0.6 R, \quad \text{decay} = 1.5$$
   - Instantaneous burst limit: $B_{\max} + \frac{\text{idle\_cap}}{\text{decay}} = 0.2 C + \frac{0.6 C}{1.5} = 0.6 C$.
2. **Burst-Ceiling Matched Mapping (v2 default)**:
   $$B_{\max} = \frac{3}{7} C, \quad B_{\text{rate}} = \frac{3}{7} R, \quad S_{\max} = \frac{9}{7} C, \quad S_{\text{rate}} = R, \quad \text{idle\_cap} = \frac{6}{7} C, \quad \text{idle\_gain} = \frac{2}{3} R, \quad \text{decay} = 1.5$$
   - Instantaneous burst limit: $B_{\max} + \frac{\text{idle\_cap}}{\text{decay}} = \frac{3}{7} C + \frac{6/7 C}{1.5} = \frac{3}{7} C + \frac{4}{7} C = C$.

### 3.3 State Update and Admission Rule (Corrected)
On request with cost $c$ at time $t$:
1. **Elapsed Time & Refill**:
   $$\Delta t = \max(0, t - t_{\text{last}}), \quad b_0 = b$$
   $$s \leftarrow \min(S_{\max}, s + \Delta t \cdot S_{\text{rate}})$$
   $$b \leftarrow \min(B_{\max}, b + \Delta t \cdot B_{\text{rate}})$$
2. **Mid-Gap Burst Fullness Time ($t_{\text{full}}$)**:
   $$t_{\text{full}} = \begin{cases} \Delta t & \text{if } b_0 \ge B_{\max} \\ 0 & \text{if } B_{\text{rate}} \le 0 \\ \max\left(0, \Delta t - \frac{B_{\max} - b_0}{B_{\text{rate}}}\right) & \text{otherwise} \end{cases}$$
   $$i \leftarrow \min(\text{idle\_cap}, i + t_{\text{full}} \cdot \text{idle\_gain})$$
   $$a \leftarrow \max(0, a - \Delta t \cdot \lambda_a) \quad \text{(v2 only)}$$
   $$t_{\text{last}} \leftarrow \max(t_{\text{last}}, t)$$
3. **Effective Decay and Admission Test**:
   $$\text{decay}_{\text{eff}} = \begin{cases} \text{decay} & \text{(v1)} \\ \text{baseDecay} + \alpha \cdot a & \text{(v2 with decay scaling)} \end{cases}$$
   $$b_{\text{eff}} = b + \frac{i}{\text{decay}_{\text{eff}}}$$
   $$c_{\text{sust}} = \begin{cases} c & \text{(v1 or v2 sustained scaling disabled)} \\ c \cdot (1 + \kappa \cdot a) & \text{(v2 with sustained scaling enabled)} \end{cases}$$
   $$\text{Decision} = \begin{cases} \text{ALLOW} & \text{if } s \ge c_{\text{sust}} \text{ and } b_{\text{eff}} \ge c \\ \text{DENY} & \text{otherwise} \end{cases}$$
4. **Consumption (if ALLOW)**:
   $$s \leftarrow s - c_{\text{sust}}$$
   $$\delta = \max(0, c - b) \quad \text{(deficit covered by credit)}$$
   $$b \leftarrow \max(0, b - c)$$
   $$i \leftarrow \max(0, i - \delta \cdot \text{decay}_{\text{eff}})$$
   $$a \leftarrow a + \frac{\delta \cdot \delta_a}{C} \quad \text{(v2 normalized)}$$

### 3.4 Mathematical Guarantees and Bounds
- **Invariant 1 (State Bounds)**: At all times, $0 \le b \le B_{\max}$, $0 \le s \le S_{\max}$, $0 \le i \le \text{idle\_cap}$, and $a \ge 0$.
- **Invariant 2 (Instantaneous Ceiling)**: The maximum admitted cost in an instantaneous step ($t = t_{\text{last}}$) is bounded by:
  $$c_{\text{burst\_max}} = \min\left(S_{\max}, B_{\max} + \frac{\text{idle\_cap}}{\text{decay}_{\text{eff}}}\right)$$
- **Theorem 1 (Cumulative Admission Bound G1)**: Over any continuous time interval $[t_0, t_0 + T]$, total admitted cost $A(T)$ satisfies:
  $$A(T) \le S_{\max} + S_{\text{rate}} \cdot T$$
  *Proof Outline:* Every admitted request of cost $c$ debits at least $c$ tokens from $s$. Since $s \le S_{\max}$ at $t_0$, sustained tokens refill at rate $S_{\text{rate}}$, and $s \ge 0$ is required for admission, total debited cost in $T$ cannot exceed the initial balance plus total refilled tokens: $S_{\max} + S_{\text{rate}} T$.

---

## 4. Implementation Details and Verification Status

### 4.1 Test Suites and Empirical Verification
Direct verification of test execution on both repositories shows:
1. **`DREB-RateLimiter`**:
   - **55 tests total** across 11 test classes (including 6 property-based tests via `jqwik` running 1,000 checks each, 1,200 random traces in `JavaRedisDifferentialTest`, 1,200 random traces in `TokenBucketDifferentialTest`, and multi-threaded Redis concurrency tests).
   - **Status:** **ALL 55 TESTS PASS** (JDK 17 and JDK 21).
2. **`DREB-Comparator`**:
   - **167 tests total** across 12 test classes (including edge cases, property invariants, contract tests, and confidence interval sanity checks).
   - **Status:** **ALL 167 TESTS PASS**.

### 4.2 Verified Implementation Fixes Already Present (Phase 1 Commit `53a5c9e`)
- **P1 Accrual Fix:** Mid-gap $t_{\text{full}}$ calculation verified in Java and Redis Lua.
- **P4 Large-Request Fix:** Admission check requires $b + i/\text{decay} \ge c$.
- **P7 Clock Rollback Fix:** $t_{\text{last}}$ monotonicity clamping enforced.
- **Precision Lossless Serialization:** Redis Lua utilizes `string.format('%.17g', x)`.
- **Redis TTL & Atomic EVAL:** Script atomic execution with configurable key TTL.
- **TokenBucket Parity:** Mirrored `sustainedTokens = burstTokens` in Java and Lua.

---

## 5. Experimental Methodology and Baseline Framework

### 5.1 Baseline Algorithms Evaluated
`DREB-Comparator` evaluates DREB against 7 baseline configurations:
1. **Token Bucket Large (`TB-large`)**: Capacity $C$, refill rate $R$.
2. **Token Bucket Small (`TB-small`)**: Capacity $B_{\max}$ ($0.2C$ or $3C/7$), refill rate $R$.
3. **Token Bucket Envelope (`TB-env`)**: Capacity $S_{\max} = 9C/7 \approx 1.286C$, refill rate $R$ (matches DREB's cumulative envelope).
4. **Leaky Bucket**: FIFO queue capacity $C$, drain rate $R$.
5. **Fixed Window**: Window 1.0s, limit $C$.
6. **Sliding Window Log**: Timestamp log, sliding window 1.0s, limit $C$.
7. **Sliding Window Counter**: Interpolated previous and current window, limit $C$.
8. **Static Dual-Bucket Family**: Burst bucket ($B_0, R_b$) and sustained bucket ($S_0, R_s$) without idle-credit accrual (evaluated across a 2-D parameter grid).

### 5.2 Simulation Methodology
- Discrete-event simulation of independent clients.
- Workloads:
  - Steady Poisson compliant traffic ($\lambda = 0.5R \dots 0.98R$).
  - Bursty compliant traffic (burst sizes with exponential inter-arrival gaps).
  - Periodic adversarial traffic ($k \cdot R$ with $k \in \{1.1, 1.25, 1.5, 2.0, 4.0\}$).
  - Adaptive credit-farming adversary (timing bursts to maximize accrued idle credit).
  - Variable request costs ($c \in \{1, 5, 20, 60, 200\}$).
- Statistical Rigor: 30 paired seeds per cell, held-out evaluation seeds (2000–2029), 95% percentile bootstrap confidence intervals.

---

## 6. Detailed Analysis of Verified Shortcomings

Through direct inspection of code, experimental results (`PAPER_TABLES.md`), and the manuscript, we verify the following critical shortcomings:

### Shortcoming 1: Idle-Credit Farming Vulnerability (Phase 1 Priority)
- **Verified Problem:** Idle credit accrues whenever the burst bucket is full, irrespective of historical consumption frequency. A periodic attacker deliberately idles for interval $\Delta t = \frac{\text{idle\_cap}}{\text{idle\_gain}}$, fires an instantaneous burst of size $B_{\max} + \frac{\text{idle\_cap}}{\text{decay}}$, and repeats.
- **Empirical Severity:**
  - In Experiment E11 (Table 14), a periodic abuser achieves an admitted rate of **$1.27R$ to $1.36R$** under burst-matched DREB, extracting $0.27R - 0.36R$ more traffic than a static baseline with identical cold burst capacity.
  - In v2, the linear dissipation of abuse score ($a \leftarrow \max(0, a - \Delta t \cdot \lambda_a)$) causes the accumulated abuse score to drop to zero during the intentional idle period. For instance, with $\lambda_a = 1.5$, an idle gap of only 1.5 seconds completely wipes out an abuse score of 2.25!
- **Root Cause:** Credit accrual is memoryless regarding long-term client burst history; abuse score decay is linear with real time rather than coupled to legitimate sustained throughput.

### Shortcoming 2: Over-Enforcement Near Contracted Rate (Phase 2 Priority)
- **Verified Problem:** Under mild overload or compliant bursty traffic near $1.0R$ (e.g. $k = 1.05 \dots 1.5R$), DREB rejects compliant or near-contract traffic that a standard Token Bucket admits.
- **Empirical Severity:**
  - In Experiment E2 (Table 9), at $k = 1.5R$, DREB admits only **$0.94R$**, rejecting $0.06R$ of legitimate contracted throughput. Token Bucket admits $1.01R$.
  - Under compliant bursty traffic (Table 11), DREB exhibits a False Rejection Rate (FRR) of **11.9%** at offered rate $0.783R$, whereas Token Bucket achieves 0% false rejection.
- **Root Cause:** Coupling of sustained bucket and burst bucket refill rates. Sizing $B_{\text{rate}} = \frac{3}{7} R$ starves the burst bucket during continuous traffic. Once burst tokens and idle credit are depleted, incoming requests can only be admitted if $b \ge c$, capping admission throughput to $B_{\text{rate}} = 0.43R$ until a pause occurs!

### Shortcoming 3: DREB v2 Parameter Ineffectiveness & Ablation Weakness (Phase 3 Priority)
- **Verified Problem:** Ablation experiments (E3, E12) demonstrate that DREB v2 provides negligible improvement over v1 at default parameters, while aggressive tuning creates unacceptable collateral damage.
- **Empirical Severity:**
  - In Table 10 (E3), with default parameters, v2 with sustained cost scaling produces virtually indistinguishable results from v2 with sustained cost scaling disabled.
  - In Table 15 (E12), tuning decay rate $\lambda_a$ down to make abuse penalties stickier reduces abuser admission slightly but causes legitimate bursty FRR to jump from 12% to over 34%!
- **Root Cause:** Linear additive formulation of decay scaling ($\text{decay}_{\text{eff}} = \text{baseDecay} + \alpha \cdot a$) and sustained cost scaling ($c_{\text{sust}} = c(1 + \kappa a)$) lacks dimensional normalization and exhibits hyper-sensitivity to traffic arrival variance.

### Shortcoming 4: Adversarial Test Framework Limitations (Phase 4 Priority)
- **Verified Problem:** The existing adversary model is predominantly an open-loop periodic burst generator.
- **Gaps:**
  - No closed-loop adaptive adversary that observes 429 response headers / retry-after delays and tunes inter-burst gaps dynamically.
  - No distributed multi-client botnet simulation (multiple IP addresses / sessions targeting the same API resource or pooling shared keys).
  - No mixed adversarial + legitimate co-flow on the same key.

### Shortcoming 5: Baseline Dominance by Static Dual Bucket (Phase 5 Priority)
- **Verified Problem:** In Experiment E9 (Table 13), a static dual-bucket rate limiter (with no idle credit mechanism whatsoever) dominates DREB in **29 out of 50 evaluated scenarios**.
- **Implication:** The added complexity of tracking idle credit, mid-gap $t_{\text{full}}$, and abuse scores is often strictly inferior to a fixed two-tier bucket with static capacities $(B_{\text{static}}, S_{\text{static}})$.
- **Research Challenge:** Must determine under what exact mathematical and traffic conditions idle conditioning provides a Pareto-superior frontier over static dual buckets.

### Shortcoming 6: Distributed Redis Validation Gaps (Phase 6 Priority)
- **Verified Problem:** The existing concurrency validation is restricted to a single JVM executing threads against a local Docker Redis instance.
- **Gaps:**
  - No multi-JVM benchmark measuring inter-node clock skew, Redis network round-trip latency, high-contention lock contention, or Redis memory footprint at millions of keys.

### Shortcoming 7: Run-to-Run Benchmark Variance (Phase 7 Priority)
- **Verified Problem:** Microbenchmarks exhibit 5% to 40% run-to-run variation due to macOS asymmetric core scheduling (M-series performance vs efficiency cores) and JVM garbage collection jitter.

### Shortcoming 8: Lack of Realistic Production Traces (Phase 8 Priority)
- **Verified Problem:** 100% of the evaluations rely on synthetic workloads (Poisson, Pareto, periodic). Real-world API traffic characteristics (diurnal patterns, self-similar traffic) are unmeasured.

---

## 7. Shortcomings Classification Matrix

To structure the project rigorously, every identified defect and research challenge is classified into one of four distinct technical categories:

| Shortcoming / Issue | Classification | Phase | Primary Artifact / Module Affected |
|---|:---:|:---:|---|
| **S1. Linear Abuse Score Dissipation** | **Code & Math** | Phase 1 | `AdaptiveIdleCreditPolicy`, Lua script |
| **S2. Memoryless Idle Credit Accrual** | **Math & Code** | Phase 1 | Refill equations, `ClientState`, Lua script |
| **S3. Burst Refill Starvation at $k \approx 1.0R$** | **Math & Code** | Phase 2 | Mapping derivations, `RefillStrategy` |
| **S4. Sustained Cost Term Hyper-Sensitivity** | **Experiments & Math** | Phase 3 | Ablation suite E3/E12, `DREBLimiterV2` |
| **S5. Absence of Closed-Loop Adaptive Adversary**| **Experiments & Code** | Phase 4 | New adversarial generator framework |
| **S6. Static Dual Bucket Dominance** | **Math & Experiments**| Phase 5 | Pareto frontier analysis, E8/E9 suites |
| **S7. Multi-JVM & Clock Skew Validation** | **Experiments & Code** | Phase 6 | Distributed test rig, Netty/Jedis harness |
| **S8. Single-Core / Asymmetric Benchmark Noise** | **Experiments** | Phase 7 | JMH harness, fixed core pinning, Linux CI |
| **S9. Synthetic-Only Workload Threat** | **Experiments** | Phase 8 | Public trace replayer (NASA/WorldCup/API) |
| **S10. Cumulative Bound Invariant Verification** | **Math** | Phase 9 | Formal induction proofs, `jqwik` properties |
| **S11. Outdated README / Facade Documentation** | **Manuscript / Docs**| Phase 10 | `README.md`, `CHANGES.md`, API docs |
| **S12. Manuscript Framing & Negative Result Reporting** | **Manuscript Only** | Phase 11/12| `DREB_Conference_Paper.docx`, LaTeX draft |

---

## 8. Detailed Classification Breakdown

### 8.1 Category A: Code-Fixable Shortcomings
Shortcomings that can be resolved directly in Java and Lua code while preserving existing mathematical specifications:
1. **README Synchronization:** Update `README.md` to document the 55 passing tests, atomic Redis Lua script capabilities, and key TTL parameters.
2. **Deterministic Adversarial Workload Generator:** Build an extensible, seed-controlled workload generator in `research/adversarial_framework/` supporting multi-key, distributed, and adaptive feedback traffic.
3. **Multi-Instance Distributed Test Harness:** Create multi-process Redis test scripts evaluating key isolation, clock skew injection, and network latency in `research/distributed_validation/`.

### 8.2 Category B: Issues Requiring New Mathematical Analysis
Shortcomings that require formal mathematical re-derivation and proof before code modification:
1. **Non-Linear / History-Dependent Credit Accrual:**
   - Redesign idle credit accrual so that consecutive burst-idle-burst cycles yield diminishing credit:
     $$i_{k+1} = f(i_k, \Delta t, \text{burst history})$$
   - Must prove that any new accrual equation preserves Invariant 1 and Theorem 1 (Cumulative Bound G1).
2. **Refill Mapping Decoupling to Eliminate Over-Enforcement:**
   - Derive an adaptive or dynamic refill rate mapping $B_{\text{rate}}(s)$ such that when $s \approx S_{\max}$ and client demand is continuous near $R$, $B_{\text{rate}}$ expands to prevent false rejection of compliant traffic.
   - Prove that instantaneous burst safety ($c_{\text{burst}} \le C$) is preserved under all initial states.
3. **Exponential / Asymmetric Abuse Score Dynamics:**
   - Replace linear time decay ($a - \Delta t \lambda_a$) with event-conditioned or sublinear dissipation that penalizes repeated cyclic farming without permanently suppressing reformed clients.

### 8.3 Category C: Issues Requiring New Experimental Frameworks
Shortcomings that require empirical infrastructure:
1. **Closed-Loop Adaptive Farming Adversary:** Simulate clients adjusting burst intervals $\tau_{\text{attack}}$ in response to rate-limiter admission outcomes.
2. **Component-Wise Ablation Matrix:** Execute multi-seed sweeps ($N \ge 30$) across the Cartesian product of v2 components (decay scaling, sustained cost scaling, abuse increment) to establish true utility.
3. **Real-World Trace Replay:** Ingest publicly available HTTP API request traces (e.g. Wikimedia / NASA / Cloud workloads) to test burstiness under realistic non-Poisson distributions.
4. **Controlled Environment Benchmarking:** Multi-run JMH harness with warm-up iterations, isolated JVMs, and documented statistical error margins.

### 8.4 Category D: Manuscript-Only Improvements
Items addressing research framing, literature positioning, and presentation:
1. **Empirical Systems Paper Framing:** Position DREB explicitly as an empirical systems study of the trade-offs of idle-conditioning rather than claiming algorithmic superiority.
2. **Related Work Systematization:** Expand literature coverage to include modern cloud rate limiters (Envoy Token Bucket, AWS WAF, Cloudflare leaky-bucket variants, RFC 2697/2698 srTCM/trTCM).
3. **Threats to Validity Expansion:** Formalize the discussion on synthetic workload limitations, core asymmetry, and parameter sensitivity.
4. **Target Journal Readiness:** Align manuscript formatting and depth with requirements for *IEEE Transactions on Network and Service Management (TNSM)* and *ACM Transactions on Computer Systems (TOCS)*.

---

## 9. Recommended Order of Execution

To ensure scientific rigor and prevent regressions, work must proceed strictly sequentially:

```
Phase 0: Audit & Baseline Freeze (Complete)
   │
   ▼
Phase 1: Address Idle-Credit Farming (Math Derivation ➔ Code Variant ➔ Evaluation)
   │
   ▼
Phase 2: Address Over-Enforcement Near Contracted Rate (Mapping Reformulation ➔ Bound Proofs)
   │
   ▼
Phase 3: Reassess DREB v2 (Ablation Matrix & Parameter Sensitivity)
   │
   ▼
Phase 4: Build Stronger Adversarial Framework (Adaptive & Distributed Workloads)
   │
   ▼
Phase 5: Expand Baseline Comparisons (Pareto-Frontier vs Static Dual Bucket)
   │
   ▼
Phase 6: Distributed Redis Validation (Multi-JVM, Contention, Clock Skew)
   │
   ▼
Phase 7: Benchmark Reliability (JMH Methodology, Noise Reduction)
   │
   ▼
Phase 8: Realistic Workload Validation (Public Trace Replay)
   │
   ▼
Phase 9: Mathematical & Implementation Verification (jqwik Formal Properties)
   │
   ▼
Phase 10: Reproducibility & Artifact Package (Docker, Run-Scripts)
   │
   ▼
Phase 11: Manuscript Comprehensive Rewrite (LaTeX / Journal Draft)
   │
   ▼
Phase 12: Journal Readiness Audit (Final Peer Review Simulation)
```

---

## 10. Verification Checkpoint

- **Git Branch:** `research/systematic-improvement` created and checked out.
- **Working Tree:** Clean (all unstaged prototype edits discarded; `53a5c9e` verified).
- **Test Integrity:** 55/55 tests in `DREB-RateLimiter` pass; 167/167 tests in `DREB-Comparator` pass.
- **Baseline Integrity:** Original implementations in `com.dreb.ratelimit` remain untouched.
