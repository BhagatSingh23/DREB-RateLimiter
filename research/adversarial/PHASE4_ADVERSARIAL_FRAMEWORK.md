# Phase 4 — Adversarial Evaluation Framework

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`f2b0730`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Primary Candidate:** DREB-RCA70  
**Comparison Baselines:** DREB-AC BM, DREB v2 Default, Static Dual Bucket (0.70C), Token Bucket (C, R)  
**Execution Test Class:** [`Phase4AdversarialTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase4AdversarialTest.java)  
**Dataset Path:** [`phase4_adversarial_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/adversarial/phase4_adversarial_raw.csv)

---

## 1. Executive Overview

Phase 4 establishes an open, reproducible, and mathematically rigorous adversarial evaluation framework for the DREB rate limiting architecture. Following the theoretical closure of the idle-credit accrual bound (Theorem 2) in Phase 1.3/1.3.1, the dual-bucket capacity reallocation in Phase 2/2.5, and the parametric ablation and G2 idle-duration audit in Phase 3/3.1, Phase 4 subjects the primary candidate—**DREB-RCA70**—to an exhaustive suite of adversarial workloads.

The objective is not to assume security based on finite tests, but to empirically probe whether RCA70's anti-farming guarantees, burst tolerance, and operational stability persist under:
1. Highly synchronized timing-aware boundary probes.
2. White-box dynamic state introspection and black-box inferred feedback.
3. Closed-loop adaptive (AIMD, binary search) controllers.
4. Mixed legitimate and adversarial multi-worker workloads.
5. Extreme boundary and pathological edge cases.

---

## 2. Framework Architecture

The evaluation framework is implemented in [`Phase4AdversarialTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase4AdversarialTest.java) using high-resolution deterministic simulation primitives.

```
+-----------------------------------------------------------------------------------+
|                           Deterministic Clock (ManualClock)                      |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------+     +-----------------------+     +-----------------------+
|  Attack Generator(s)  | --> |     LimiterHarness    | --> | TrackingIdlePolicy    |
|  (17 Attack Variants) |     |  peekState() / allow()|     |  totalCreditSpent()   |
+-----------------------+     +-----------------------+     +-----------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|               Raw Metric Collector & CSV Emitter (30 Seeds / Run)                 |
+-----------------------------------------------------------------------------------+
```

### 2.1 Core Components

1. **Deterministic Time Control (`ManualClock`):**
   Time progresses strictly monotonically across scheduled attack events. No wall-clock sleeps or thread scheduling non-determinism can alter execution paths.

2. **Instrumented Credit Tracking (`TrackingIdlePolicy`):**
   Wraps the limiter's underlying `IdleCreditPolicy` to intercept and measure every unit of idle credit consumed during request admission:
   $$\text{deficit} = \max(0, c - B(t))$$
   $$\text{creditDrawn} = \sum \min(I_{\text{eff}}(t), \text{deficit})$$
   This separates total admitted tokens into **credit-funded tokens** and **ordinary refill-funded tokens**.

3. **Dynamic Limiter Harness (`LimiterHarness`):**
   Provides uniform admission invocation (`allow(clientId, cost)`), dynamic internal state inspection (`peekState()`), and cumulative credit logging across all 5 evaluated rate limiter configurations.

---

## 3. Evaluation Matrix

The evaluation covers 5 limiter configurations across 17 adversarial strategies, replicated across 30 independent pseudo-random seeds ($N=30$, seeds $5000 \dots 5029$) over a consistent $T = 60.0\,\text{s}$ horizon, yielding:
$$\text{Total Experimental Runs} = 5 \times 17 \times 30 = 2,550\,\text{executions}$$

### 3.1 Evaluated Policy Configurations

| Policy Identifier | Display Name | Allocation ($\alpha$) | $B_{\max}$ | $R_{\text{burst}}$ | $S_{\max}$ | Idle Policy | Credit Parameters |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`rca70`** | **DREB-RCA70** | $0.70$ | $700$ | $1666.67$ | $1285.71$ | Adaptive Ceiling | $\text{baseCap}=450, \text{baseDecay}=1.5, \beta=0.75, \lambda_{\text{rec}}=0.05$ |
| **`dreb_ac_bm`** | DREB-AC BM | $0.30$ | $428.57$ | $1666.67$ | $1285.71$ | Adaptive Ceiling | $\text{baseCap}=857.14, \text{baseDecay}=1.5, \beta=0.75, \lambda_{\text{rec}}=0.05$ |
| **`dreb_v2_default`** | DREB v2 Default | $0.30$ | $428.57$ | $1666.67$ | $1285.71$ | Adaptive Idle | Heuristic $K$ tracking, $\text{baseCap}=857.14, \text{baseDecay}=1.0$ |
| **`static_db_070c`** | Static DB (0.70C) | $0.70$ | $700$ | $1666.67$ | $1285.71$ | Linear (Disabled) | $\text{idleRefill}=0, \text{idleCap}=0$ (No idle credit) |
| **`tb_large`** | Token Bucket | $1.00$ | $1000$ | $1000.00$ | $1000.00$ | N/A | Classical Token Bucket ($C=1000, R=1000$) |

### 3.2 Attack Families

1. **Family A: Timing-Aware Credit Farming:** Exploits refill boundaries, micro-pulsing, periodic full bursts, and sinusoidal wait intervals.
2. **Family B: State-Aware Attacks:** Compares dynamic white-box optimal adversaries against black-box inferred boundary searchers.
3. **Family C: Closed-Loop Adaptive Attacks:** Feedback-driven adversaries adjusting wait time (AIMD), cost (binary search), and credit synchronization.
4. **Family D: Mixed Legitimate and Adversarial Traffic:** Concurrent Poisson legitimate traffic, multi-worker collisions, and post-attack recovery.
5. **Family E: Boundary and Pathological Strategies:** Exact $B_{\max}$ boundary, capacity limit $C$, micro-cost streams ($c=1$), unadmissible overdraw ($c=1500$), and bimodal toggling.

---

## 4. Threat Models

### 4.1 Threat Model 1: White-Box Optimal Adversary
- **Attacker Knowledge:** Complete real-time observability of limiter internal state:
  $$\mathbf{x}(t) = \langle B(t), S(t), I(t), K(t) \rangle$$
  along with known configuration constants ($\alpha, R, C, \beta, \lambda_{\text{rec}}$).
- **Attacker Capability:** Dynamically crafts requests matching exact permitted capacities:
  $$c^*(t) = \max\left(1, \left\lfloor \min\left(S(t), B(t) + \frac{I(t)}{1.5}\right) \right\rfloor\right)$$
- **Objective:** Extract maximum possible tokens without ever incurring an HTTP 429 rejection.

### 4.2 Threat Model 2: Black-Box Adaptive Adversary
- **Attacker Knowledge:** Zero direct access to internal state or configuration constants.
- **Attacker Observability:** Binary admission feedback (HTTP 200 `ALLOW` vs HTTP 429 `DENY`) and wall-clock response timing.
- **Attacker Capability:** Dynamically adapts burst interval $\tau$ and request cost $c$ via AIMD or binary search controllers.
- **Objective:** Probe decision boundaries to maximize throughput and idle credit extraction.

### 4.3 Threat Model 3: Multi-Worker Concurrent Adversary
- **Attacker Knowledge:** Decentralized, unsynchronized attack workers.
- **Attacker Capability:** Independent parallel workers submitting bursts against a shared client identifier without central coordination.
- **Objective:** Test state corruption and race dynamics under uncoordinated burst collisions.

---

## 5. Metric Definitions

All metrics are computed over the active measurement horizon $T = 60.0\,\text{s}$ with contracted rate $R = 1000\,\text{tokens/s}$.

1. **Normalized Total Admitted Rate ($r_{\text{tot}}$):**
   $$r_{\text{tot}} = \frac{\sum_{i \in \mathcal{A}_{\text{all}}} c_i}{R \cdot T}$$
2. **Normalized Adversarial Admitted Rate ($r_{\text{adv}}$):**
   $$r_{\text{adv}} = \frac{\sum_{i \in \mathcal{A}_{\text{adv}}} c_i}{R \cdot T}$$
3. **Normalized Credit-Funded Rate ($r_{\text{cred}}$):**
   $$r_{\text{cred}} = \frac{\text{Total Credit Consumed}}{R \cdot T}$$
4. **Normalized Ordinary Refill Rate ($r_{\text{ord}}$):**
   $$r_{\text{ord}} = \max\left(0, r_{\text{adv}} - r_{\text{cred}}\right)$$
5. **Legitimate False Rejection Rate ($\text{FRR}_{\text{legit}}$):**
   $$\text{FRR}_{\text{legit}} = \frac{\sum_{i \in \mathcal{R}_{\text{legit}}} c_i - \sum_{i \in \mathcal{A}_{\text{legit}}} c_i}{\sum_{i \in \mathcal{R}_{\text{legit}}} c_i} \times 100\%$$
6. **Request Admission Ratio ($\text{AR}$):**
   $$\text{AR} = \frac{|\mathcal{A}|}{|\mathcal{R}|} \times 100\%$$
7. **Terminal Abuse Index ($K_T$):**
   $$K_T = K(T)$$

---

## 6. Execution Instructions and Reproducibility

### 6.1 Prerequisites
- **Operating System:** macOS Darwin 24.3.0 (arm64)
- **Java Development Kit:** JDK 21.0.9 (Temurin / LTS)
- **Bytecode Target:** Java 21 (`-source 21 -target 21`)
- **Build Tool:** Apache Maven 3.9.x

### 6.2 Test Execution Command
To execute the complete adversarial evaluation harness and generate the raw dataset:
```bash
mvn test -Dtest=Phase4AdversarialTest
```

### 6.3 Verification of Output
The test writes the complete dataset to:
```bash
research/adversarial/phase4_adversarial_raw.csv
```
Confirming row count:
```bash
wc -l research/adversarial/phase4_adversarial_raw.csv
# Expected: 2551 (1 header row + 2,550 data rows)
```
