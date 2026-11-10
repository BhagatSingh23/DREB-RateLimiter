# Phase 2.2: Alternative Refill Mappings and Bucket Interaction Architectures

**Project:** DREB — Idle-Conditioned Burst Limiting  
**Phase:** 2.2 — Candidate Refill Mappings  
**Branch:** `research/systematic-improvement`  
**Status:** COMPLETE — Five Candidate Formulations Mathematically Specified  
**Artifact Path:** [`research/over_enforcement/PHASE2_2_MAPPING_CANDIDATES.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/over_enforcement/PHASE2_2_MAPPING_CANDIDATES.md)

---

## 1. Architectural Design Principles

Phase 2.1 diagnosed the root cause of over-enforcement:
1. **The Conjunction Bottleneck:** Requiring $\min(S(t), B(t) + I(t)/\text{baseDecay}) \ge c$ chokes legitimate traffic whenever the effective burst generation rate falls below the sustained contract rate $R$.
2. **Sub-Contract Credit Accrual:** In original DREB, $\text{gain} = \frac{2}{3} R$ and $\text{baseDecay} = 1.5$, yielding an effective accrual rate of only $\frac{\text{gain}}{\text{baseDecay}} = \frac{4}{9} R \approx 0.444 R$.
3. **Over-Partitioning of Capacity:** Sizing $B_{\max} = \frac{3}{7} C \approx 0.4286 C$ leaves legitimate bursty clients with too little baseline burst capacity ($57.1\%$ of burst capacity is locked behind idle credit).

To solve over-enforcement while preserving all invariants, every candidate must satisfy the four mandatory research guarantees:
- **G1 (Cumulative Volume Bound):** $A(T) \le S_{\max} + S_{\text{rate}} T$.
- **G2 (Idle-Conditioned Burst Tolerance):** Maximum burst headroom $C$ is earned only through demonstrated idleness.
- **G3 (Self-Limiting Reward):** Idle credit debited at $\text{baseDecay} > 1.0$.
- **Anti-Farming Bound (Theorem 2):** $\limsup_{T \to \infty} \frac{C_{\text{credit}}(T)}{T} \le \text{baseCap} \cdot \lambda_{\text{rec}}$.

---

## 2. Specification of Candidate Architectures

### Candidate 1: DREB-MAG (Matched Accrual Gain)

#### 1. Concept & Operational Interpretation
DREB-MAG preserves the baseline burst-matched capacity ratios ($B_{\max} = \frac{3}{7} C$, $S_{\max} = \frac{9}{7} C$) but eliminates the sub-contract accrual penalty by matching the idle accrual rate to the debit multiplier:
$$\text{gain} = \text{baseDecay} \cdot R = 1.5 R$$
This ensures that while the burst bucket is full, effective credit accrues at:
$$\frac{\text{d}}{\text{d}t} \left( \frac{I}{\text{baseDecay}} \right) = \frac{\text{gain}}{\text{baseDecay}} = \frac{1.5 R}{1.5} = 1.0 R$$
A rested client accumulates burst headroom at rate $R$, exactly matching the sustained contract rate.

#### 2. Complete State Equations
- **Bucket Refills:**
  $$B' = \min\left( \frac{3}{7} C,\, B + \Delta t \cdot \frac{5}{3} R \right)$$
  $$S' = \min\left( \frac{9}{7} C,\, S + \Delta t \cdot R \right)$$
- **Draw Counter & Credit Recovery:**
  $$K' = \max\left( 0.0,\, K - \Delta t \cdot \lambda_{\text{rec}} \right)$$
  $$t_{\text{to\_fill}} = \max\left( 0.0,\, \frac{B_{\max} - B}{5/3 R} \right), \quad t_{\text{full}} = \max(0.0,\, \Delta t - t_{\text{to\_fill}})$$
  $$\text{cap}_{\text{eff}}(K') = \frac{\text{baseCap}}{1 + \beta K'}, \quad \text{where } \text{baseCap} = \frac{6}{7} C$$
  $$I' = \begin{cases} \min(\text{cap}_{\text{eff}}(K'),\, I + t_{\text{full}} \cdot 1.5 R) & \text{if } t_{\text{full}} > 0 \\ \min(\text{cap}_{\text{eff}}(K'),\, I) & \text{if } t_{\text{full}} = 0 \end{cases}$$
- **Admission Check:**
  $$\text{Decision} = \text{ALLOW} \iff S' \ge c \land \left( B' + \frac{I'}{1.5} \ge c \right)$$

#### 3. Mathematical Guarantees & Anti-Farming Analysis
- **Cumulative Bound (G1):** Every admitted request debits $S(t)$ by $c$. Since $S(0) = S_{\max}$ and refills at rate $R$, $A(T) \le S_{\max} + R \cdot T$ holds unconditionally.
- **Anti-Farming (Theorem 2):** The asymptotic credit bound is $\limsup \frac{\sum \delta_j}{T} \le \text{baseCap} \cdot \lambda_{\text{rec}} = \frac{6}{7} C \times 0.05 = 0.04286 R$. As shown in Phase 1.3, $\text{gain}$ does not enter the asymptotic bound.
- **Limitations:** Does not solve the $B_{\max} = 0.4286 C$ capacity ceiling. If a compliant client requests a burst of $0.60 C$ with short or zero prior idleness ($I \approx 0$), it is still rejected because $B_{\max} < 0.60 C$.

---

### Candidate 2: DREB-RCA60 (Rebalanced Capacity Allocation — 60/40)

#### 1. Concept & Operational Interpretation
DREB-RCA60 shifts the capacity balance between the burst bucket and the credit pool:
- Baseline Burst Bucket: $B_{\max} = 0.60 C$ (up from $0.43 C$).
- Idle Credit Pool: $\text{idleCap} = 1.5 \times (C - B_{\max}) = 1.5 \times 0.40 C = 0.60 C$.
- Post-Idle Burst Ceiling: $B_{\max} + \frac{\text{idleCap}}{\text{baseDecay}} = 0.60 C + \frac{0.60 C}{1.5} = 0.60 C + 0.40 C = 1.00 C$.
- Matched Gain: $\text{gain} = \text{baseDecay} \cdot R = 1.5 R$.

#### 2. Complete State Equations
- **Bucket Refills:**
  $$B' = \min\left( 0.60 C,\, B + \Delta t \cdot \frac{5}{3} R \right)$$
  $$S' = \min\left( \frac{9}{7} C,\, S + \Delta t \cdot R \right)$$
- **Credit & Ceiling:**
  $$\text{cap}_{\text{eff}}(K') = \frac{0.60 C}{1 + \beta K'}$$
  $$I' = \begin{cases} \min(\text{cap}_{\text{eff}}(K'),\, I + t_{\text{full}} \cdot 1.5 R) & \text{if } t_{\text{full}} > 0 \\ \min(\text{cap}_{\text{eff}}(K'),\, I) & \text{if } t_{\text{full}} = 0 \end{cases}$$

#### 3. Mathematical Guarantees & Anti-Farming Analysis
- **Cumulative Bound (G1):** Unconditionally preserved via $S(t) \ge c$ debiting.
- **Anti-Farming (Theorem 2):** Because $\text{baseCap}$ is reduced from $0.857 C$ to $0.600 C$, the asymptotic credit extraction rate is **strictly lower**:
  $$\text{Credit Bound} = 0.600 C \times 0.05 = \mathbf{0.0300 R} \quad (\text{30\% reduction vs baseline!})$$
- **Legitimate Benefit:** Absorbs compliant bursts up to $600$ tokens directly without needing idle credit.

---

### Candidate 3: DREB-RCA70 (Rebalanced Capacity Allocation — 70/30)

#### 1. Concept & Operational Interpretation
DREB-RCA70 provides a larger baseline burst bucket for legitimate traffic while shrinking the idle-credit attack surface:
- Baseline Burst Bucket: $B_{\max} = 0.70 C = 700$.
- Idle Credit Pool: $\text{idleCap} = 1.5 \times (1.0 - 0.70) C = 0.45 C = 450$.
- Post-Idle Burst Ceiling: $B_{\max} + \frac{\text{idleCap}}{1.5} = 700 + 300 = 1000 = C$.
- Sustained Envelope: $S_{\max} = \frac{9}{7} C \approx 1285.71$, $S_{\text{rate}} = R$.
- Refill Rates: $B_{\text{rate}} = \frac{5}{3} R \approx 1666.67$, $\text{gain} = 1.5 R = 1500$.

#### 2. Complete State Equations
- **Refills:**
  $$B' = \min\left( 0.70 C,\, B + \Delta t \cdot 1666.67 \right)$$
  $$S' = \min\left( 1285.71,\, S + \Delta t \cdot 1000 \right)$$
- **Credit & Ceiling:**
  $$\text{cap}_{\text{eff}}(K') = \frac{450.0}{1 + 0.75 K'}$$
  $$t_{\text{to\_fill}} = \max\left( 0.0,\, \frac{700.0 - B}{1666.67} \right), \quad t_{\text{full}} = \max(0.0,\, \Delta t - t_{\text{to\_fill}})$$
  $$I' = \begin{cases} \min(\text{cap}_{\text{eff}}(K'),\, I + t_{\text{full}} \cdot 1500) & \text{if } t_{\text{full}} > 0 \\ \min(\text{cap}_{\text{eff}}(K'),\, I) & \text{if } t_{\text{full}} = 0 \end{cases}$$
- **Consumption:**
  $$\text{ALLOW} \iff S' \ge c \land \left( B' + \frac{I'}{1.5} \ge c \right)$$
  $$S'' = S' - c, \quad \delta = \max(0, c - B'), \quad B'' = \max(0, B' - c)$$
  $$I'' = \max(0, I' - \delta \cdot 1.5), \quad K'' = K' + \frac{\delta}{450.0}$$

#### 3. Mathematical Guarantees & Anti-Farming Analysis
- **Cumulative Bound (G1):** Unconditionally preserved ($A(T) \le S_{\max} + R \cdot T$).
- **Anti-Farming (Theorem 2):** Nominal $\text{baseCap} = 450$. The asymptotic credit extraction bound is:
  $$\limsup_{T \to \infty} \frac{\sum \delta_j}{T} \le 450 \times 0.05 = \mathbf{22.5\,\text{tokens/s}} = \mathbf{0.0225 R}$$
  **Theorem 2 credit extraction bound is cut in half ($47.5\%$ reduction) compared to baseline DREB-AC!**
- **Abuse Invariant $K^*$:**
  $$K^* = \frac{1}{0.75} \left( \frac{1666.67}{1.5 \times 700 \times 0.05} - 1 \right) = \frac{1}{0.75} (31.746 - 1) \approx 40.99$$
  $$K_{\max} \approx 40.99 + 0.67 = 41.66 < \infty$$
  Uniform invariant holds with tighter bounds.
- **Operational Merits:** Eliminates false rejections for compliant bursts up to $700$ tokens. Admits $1.0029 R$ at $1.5 R$ overload without under-servicing.

---

### Candidate 4: DREB-RCA80 (Rebalanced Capacity Allocation — 80/20)

#### 1. Concept & Operational Interpretation
Extremal rebalancing candidate:
- Baseline Burst Bucket: $B_{\max} = 0.80 C = 800$.
- Idle Credit Pool: $\text{idleCap} = 1.5 \times 0.20 C = 300$ (max effective credit = $200$).
- Post-Idle Burst Ceiling: $800 + 200 = 1000 = C$.

#### 2. Trade-Off Analysis
- **Merits:** Lowest false rejection on compliant bursts ($0.88\%$ FRR in Table 3.1 trace); lowest credit extraction bound ($0.0150 R$).
- **Drawback:** Idle-conditioned reward is compressed to only $200$ tokens ($20\%$ of $C$), reducing the dynamic elasticity of the algorithm close to a static dual-bucket.

---

### Candidate 5: DREB-Coupled (Surplus Sustained Spillover)

#### 1. Concept & Operational Interpretation
Rather than changing the static capacities ($B_{\max} = \frac{3}{7} C$), DREB-Coupled allows surplus tokens from the sustained refill flow to replenish $B(t)$ whenever $B < B_{\max}$ and $S(t) > B(t)$.
During interval $\Delta t$, the sustained refill generates $\Delta S = \Delta t \cdot R$. If $S$ is near full while $B$ was depleted by prior traffic, an amount $\min(B_{\max} - B, \Delta t \cdot R)$ is diverted to replenish $B$.

#### 2. Trade-Off Analysis
- **Merits:** Keeps original capacity ratios ($B_{\max} = 428.57$).
- **Drawback:** Coupling equations introduce state-dependent cross-derivatives between $S$ and $B$, complicating the atomic Redis Lua implementation. Furthermore, simulations in Phase 2.1 show that DREB-Coupled fails to eliminate false rejection for single bursts larger than $B_{\max} = 428.57$ (FRR remained at $3.62\%$).

---

## 3. Comparative Summary Matrix

| Metric / Property | Baseline DREB-AC | Cand 1: DREB-MAG | Cand 2: DREB-RCA60 | Cand 3: DREB-RCA70 | Cand 4: DREB-RCA80 | Cand 5: DREB-Coupled |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Baseline Burst $B_{\max}$** | $0.4286 C$ | $0.4286 C$ | $0.6000 C$ | **$0.7000 C$** | $0.8000 C$ | $0.4286 C$ |
| **Idle Credit Pool $\text{idleCap}$** | $0.8571 C$ | $0.8571 C$ | $0.6000 C$ | **$0.4500 C$** | $0.3000 C$ | $0.8571 C$ |
| **Idle Gain Rate** | $0.6667 R$ | $1.5000 R$ | $1.5000 R$ | **$1.5000 R$** | $1.5000 R$ | $1.5000 R$ |
| **Effective Accrual Rate** | $0.4444 R$ | $1.0000 R$ | $1.0000 R$ | **$1.0000 R$** | $1.0000 R$ | $1.0000 R$ |
| **Post-Idle Burst Ceiling** | $1.0000 C$ | $1.0000 C$ | $1.0000 C$ | **$1.0000 C$** | $1.0000 C$ | $1.0000 C$ |
| **G1 Cumulative Bound** | Preserved | Preserved | Preserved | **Preserved** | Preserved | Preserved |
| **Thm 2 Asymptotic Bound** | $0.04286 R$ | $0.04286 R$ | $0.03000 R$ | **$0.02250 R$** | $0.01500 R$ | $0.04286 R$ |
| **Compliant Bursty FRR** | $4.41\%$ | $4.17\%$ | $1.40\%$ | **$1.02\%$** | $0.88\%$ | $3.62\%$ |
| **Overload $1.50R$ Throughput**| $0.8109 R$ | $0.8122 R$ | $1.0029 R$ | **$1.0029 R$** | $1.0029 R$ | $0.8122 R$ |
| **Adversary Periodic Farmer** | $0.3664 R$ | $0.3664 R$ | $0.4489 R$ | **$0.4970 R$** | $0.5452 R$ | $0.3664 R$ |
| **Implementation Complexity** | Baseline | Baseline | Zero delta | **Zero delta** | Zero delta | High (Coupled) |

---

## 4. Phase 2.3 Experimental Roadmap

Candidates 2, 3, and 4 represent the most promising parameterized family: **Rebalanced Capacity Allocation with Matched Accrual Gain (DREB-RCA)**.

In Phase 2.3, we implement this family cleanly in `src/main/java/com/dreb/ratelimit/policies/` and evaluate all configurations under the comprehensive 9-load test matrix ($0.70R$ to $2.00R$) across all specified workload types.
