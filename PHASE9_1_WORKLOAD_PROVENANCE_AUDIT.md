# PHASE 9.1: WORKLOAD PROVENANCE AND SYNTHETIC CHARACTERIZATION AUDIT

**Audit Date:** November 2026  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `c8df6d4`  
**Auditor:** Independent Red-Team Review  

---

## 1. Executive Summary

A core claim presented in Phase 9 (`DREB_RCA70_MANUSCRIPT_DRAFT.md`, Section 6.1, Section 7, and `PHASE9_CLAIM_RECONCILIATION.md`) states that DREB-RCA70 was evaluated against a **"19-workload benchmark suite"**, comprising:
- "8 real-world microservice traces (W01–W03, W11–W15)"
- "6 synthetic stress & burst profiles (W05–W07, W16–W18)"
- "5 adversarial credit-farming suites (W04, W08–W10, W19)"

This independent red-team audit inspected every source code file, test generator, and raw CSV in the repository to trace the empirical provenance of these 19 workloads.

### Key Finding
**The claim of 19 workloads containing 8 real-world microservice traces is UNSUBSTANTIATED and FACTUALLY ERRONEOUS.**
1. **Workloads W11 through W19 do not exist.** No generators, configuration blocks, or raw data rows exist in the repository for identifiers W11–W19.
2. **Workloads W01 through W10 are 100% synthetic programmatic generators.** None of them are "real-world traces derived from production telemetry." They are synthetic Java loops using Poisson distributions, step functions, and periodic sleeps implemented in `Phase5BaselineComparisonTest.java`.
3. **The true historical experimental suite** established in Phase 5 and confirmed in Phase 8 consists of:
   - Exactly **10 synthetic workloads (W01–W10)** in `research/comparison/phase5_baseline_comparison_raw.csv` (1,500 simulation runs across 30 seeds).
   - Exactly **17 synthetic attack generators (Families A1–E5)** in `research/adversarial/phase4_adversarial_raw.csv`.
   - JMH nanosecond in-memory microbenchmarks (1,500 runs).
   - Redis cluster multi-threaded live tests.

This audit details the exact code provenance of all existing workloads and establishes the necessary corrections to restore scientific integrity to the manuscript.

---

## 2. Workload Inventory: Claimed vs. Actual

| Workload ID | Claimed Classification in Phase 9 Draft | Actual Code Status | Generator Implementation | True Workload Nature |
|---|---|---|---|---|
| **W01** | Real-World Microservice Trace | Exists in `Phase5BaselineComparisonTest` | `samplePoisson(0.98 * R * step, rng)` | Synthetic Poisson arrival stream at $0.98R$ |
| **W02** | Real-World Microservice Trace | Exists in `Phase5BaselineComparisonTest` | Periodic loop: 5s idle, then 1,000-token burst | Synthetic periodic on/off burst profile |
| **W03** | Real-World Microservice Trace | Exists in `Phase5BaselineComparisonTest` | Poisson stream with rate $1.50R$ | Synthetic sustained overload stream |
| **W04** | Adversarial Credit-Farming Suite | Exists in `Phase5BaselineComparisonTest` | Competing legitimate Poisson ($0.40R$) + periodic burst (700 tok every 1.5s) | Synthetic mixed competing traffic |
| **W05** | Synthetic Stress & Burst Profile | Exists in `Phase5BaselineComparisonTest` | Periodic adversarial pulse: 750 tokens every 0.470s | Synthetic timing-farming probe |
| **W06** | Synthetic Stress & Burst Profile | Exists in `Phase5BaselineComparisonTest` | Dynamic oracle query requesting maximum instantaneous balance | Synthetic white-box extraction probe |
| **W07** | Synthetic Stress & Burst Profile | Exists in `Phase5BaselineComparisonTest` | Binary search / probing around burst boundaries | Synthetic black-box threshold probe |
| **W08** | Adversarial Credit-Farming Suite | Exists in `Phase5BaselineComparisonTest` | AIMD probe adjusting idle interval based on HTTP 429 response | Synthetic closed-loop adaptive adversary |
| **W09** | Adversarial Credit-Farming Suite | Exists in `Phase5BaselineComparisonTest` | Multi-worker concurrent pulses on shared tenant key | Synthetic multi-client collision pattern |
| **W10** | Adversarial Credit-Farming Suite | Exists in `Phase5BaselineComparisonTest` | Attack burst followed by legitimate traffic recovery observation | Synthetic post-attack recovery test |
| **W11** | Real-World Microservice Trace | **DOES NOT EXIST** | N/A | Non-existent |
| **W12** | Real-World Microservice Trace | **DOES NOT EXIST** | N/A | Non-existent |
| **W13** | Real-World Microservice Trace | **DOES NOT EXIST** | N/A | Non-existent |
| **W14** | Real-World Microservice Trace | **DOES NOT EXIST** | N/A | Non-existent |
| **W15** | Real-World Microservice Trace | **DOES NOT EXIST** | N/A | Non-existent |
| **W16** | Synthetic Stress & Burst Profile | **DOES NOT EXIST** | N/A | Non-existent |
| **W17** | Synthetic Stress & Burst Profile | **DOES NOT EXIST** | N/A | Non-existent |
| **W18** | Synthetic Stress & Burst Profile | **DOES NOT EXIST** | N/A | Non-existent |
| **W19** | Adversarial Credit-Farming Suite | **DOES NOT EXIST** | N/A | Non-existent |

---

## 3. Detailed Provenance of Existing Synthetic Workloads (W01–W10)

All 10 evaluated workloads are implemented in [`Phase5BaselineComparisonTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase5BaselineComparisonTest.java#L210-L315) and recorded in [`phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv).

### W01: Benign Steady Traffic (`W01_benign_steady`)
- **Source Code:** Lines 211–222 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** Programmatic Poisson process. Step size $\Delta t = 0.05\,\text{s}$. Mean arrival $\lambda = 0.98 \times R \times \Delta t = 49.0\,\text{tokens/step}$.
- **Duration:** 60.0 seconds. Total requests: 1,200. Total offered volume: ~58,800 tokens.
- **Seeds:** 30 pseudo-random seeds (5000–5029).
- **Provenance:** Fully synthetic. No external trace file.

### W02: Legitimate On/Off Bursting (`W02_legitimate_on_off_burst`)
- **Source Code:** Lines 224–239 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** Deterministic on/off pattern. Base Poisson stream at $0.20R$ interspersed with a batch spike of $c = 1,000\,\text{tokens}$ every 10.0 seconds.
- **Duration:** 60.0 seconds. Total legitimate burst volume: 6 bursts $\times 1,000 = 6,000\,\text{tokens}$.
- **Provenance:** Fully synthetic model designed to test the Quiescence Paradox.

### W03: Sustained Overload (`W03_sustained_overload`)
- **Source Code:** Lines 241–251 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** Poisson stream at $\lambda = 1.50R = 1,500\,\text{tokens/s}$.
- **Duration:** 60.0 seconds. Total offered: ~90,000 tokens.
- **Provenance:** Fully synthetic stress test to verify strict rate conformance under continuous overload.

### W04: Mixed Competing Traffic (`W04_mixed_competing`)
- **Source Code:** Lines 253–265 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** Combines legitimate background traffic ($\lambda = 0.40R$) with an adversarial pulse stream (burst $c = 700\,\text{tokens}$ offered every 1.5 seconds, tagged `isAdversary = true`).
- **Duration:** 60.0 seconds.
- **Provenance:** Fully synthetic dual-client competition harness.

### W05: Timing-Aware Credit Farming (`W05_timing_farming`)
- **Source Code:** Lines 267–275 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** Adversary idles for $\Delta t = 0.470\,\text{s}$ (the primary bucket fill time from zero plus 50 ms), then issues $c = 750\,\text{tokens}$.
- **Duration:** 60.0 seconds.
- **Provenance:** Fully synthetic timing attack modeled after Attack A1 in Phase 4.

### W06: White-Box Oracle Exploitation (`W06_white_box_oracle`)
- **Source Code:** Lines 277–286 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** Adversary inspects limiter state and requests the exact liquid capacity available at 0.50 s intervals.
- **Provenance:** Fully synthetic oracle probe.

### W07: Black-Box Adaptive Probing (`W07_black_box_probe`)
- **Source Code:** Lines 288–299 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** Probes capacity by incrementing cost $c$ until an HTTP 429 is encountered, then backs off.
- **Provenance:** Fully synthetic black-box behavioral probe.

### W08: AIMD Adaptive Adversary (`W08_aimd_adaptive`)
- **Source Code:** Lines 301–312 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** Modulates pulse period via Additive Increase Multiplicative Decrease (AIMD) based on admission feedback.
- **Provenance:** Fully synthetic adaptive adversarial simulation.

### W09: Multi-Worker Key Collision (`W09_multi_worker_collision`)
- **Source Code:** Lines 314–325 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** 5 parallel simulated threads issuing unsynchronized micro-bursts against a shared client key.
- **Provenance:** Fully synthetic concurrency stress harness.

### W10: Post-Attack Quiescent Recovery (`W10_post_attack_recovery`)
- **Source Code:** Lines 327–340 of `Phase5BaselineComparisonTest.java`.
- **Generator Logic:** Saturated attack for $t \in [0, 20\,\text{s}]$, followed by complete silence for $t \in [20, 40\,\text{s}]$, followed by legitimate burst at $t = 40\,\text{s}$.
- **Provenance:** Fully synthetic lifecycle verification harness.

---

## 4. Phase 4 Closed-Loop Adversarial Attack Families (17 Attack Profiles)

In addition to W01–W10, the repository contains 17 distinct adversarial attack generators implemented in [`Phase4AdversarialTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase4AdversarialTest.java#L160-L315) and recorded in `research/adversarial/phase4_adversarial_raw.csv`:
- **Family A (Timing-Aware):** `A1_micro_pulsing`, `A2_refill_boundary_jitter`, `A3_periodic_full_burst`, `A4_phase_shifted_sinusoidal`.
- **Family B (Parameter-Inference):** `B1_white_box_optimal`, `B2_black_box_inferred`.
- **Family C (Adaptive Closed-Loop):** `C1_aimd_adaptive_interval`, `C2_binary_search_cost`, `C3_credit_maximizer`.
- **Family D (Multi-Client Coordinated):** `D1_competing_legitimate`, `D2_multi_worker_collision`, `D3_post_attack_recovery`.
- **Family E (Boundary & Overdraw):** `E1_near_bmax_boundary`, `E2_near_capacity_boundary`, `E3_micro_cost_flood`, `E4_unadmissible_overdraw`, `E5_bimodal_alternation`.

All 17 attack profiles are synthetic simulations.

---

## 5. Required Manuscript Corrections

To eliminate unsubstantiated claims and maintain rigorous academic integrity:

1. **Delete all references to "19 workloads"**: Replace with **"10 comprehensive synthetic workloads (W01–W10) and 17 closed-loop adversarial attack families (Families A–E)"**.
2. **Delete all claims of "real-world microservice traces derived from production telemetry"**:
   - Replace with: **"programmatic workload generators designed to simulate real-world microservice traffic patterns (Poisson arrival streams, on/off batch synchronization spikes, and step-function overloads)"**.
3. **Explicitly disclose the synthetic nature in Threats to Validity (Section 9)**:
   - State transparently: *"All evaluations utilize programmatic synthetic generators and closed-loop attack models rather than anonymized production gateway access logs. While these generators accurately capture stochastic arrival distributions, burst ratios, and adversarial timing probes, they do not replicate multi-tenant tail anomalies observed in enterprise production clusters."*
