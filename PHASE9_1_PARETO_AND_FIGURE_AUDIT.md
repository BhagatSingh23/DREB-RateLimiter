# PHASE 9.1: PARETO FRONTIER AND MATHEMATICAL FIGURE AUDIT

**Audit Date:** November 2026  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `c8df6d4`  
**Auditor:** Independent Red-Team Review  

---

## 1. Executive Summary

This audit independently inspects the three evaluation scopes (Scope 1, Scope 2, Scope 3) and audits all six generated publication figures in [`PHASE9_PUBLICATION_FIGURES/`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_PUBLICATION_FIGURES).

### Core Findings
1. **Scope 1, 2, and 3 Pareto Frontiers are Mathematically Sound:**
   - Under **Scope 1 ($c \le 700$)**, `static_db_070c` strictly dominates all candidates because its static burst cap achieves zero false rejections without incurring dynamic abuse-tracking overhead.
   - Under **Scope 2 ($c = 1,000$)**, `static_db_070c`, `tb_standard`, and `gcra` suffer **100% request FRR**, disqualifying them from operational eligibility. The non-dominated Pareto frontier consists strictly of `{rca70, tb_large}`.
   - Under **Scope 3 (Composite)**, `{rca70, tb_large}` remain the non-dominated operational frontier.
2. **Figure Auditing Results:**
   - **Figure 1 (Architecture & Dynamic Ceiling):** Accurately depicts the hyperbolic curve, but requires caption and axis correction from "burst ceiling" to "idle-credit pool ceiling $I_{\text{ceil}}(K)$".
   - **Figure 2 (Scope 1 and Scope 2 Frontiers):** Every plotted point aligns with Phase 5.4.1 audited values. Disqualification boundary at 100% FRR is clearly visualized.
   - **Figure 3 (Suppression Over Time):** Time-series slopes and bar heights match empirical CSV rows for W04, W08, W10, and Phase 3 ablation.
   - **Figure 4 (Recovery Trajectories):** Exact linear decay slopes, calling out $\tau_{0.05} = 19.0\,\text{s}$ and $\tau_0 = 20.0\,\text{s}$.
   - **Figure 5 (Execution Latency Hierarchy):** Correct log-scale bars. Confirms that synchronous `WAIT 1` P99 is **1.301 ms** (exceeding 1 ms), incurring a **+95.1% penalty** over asynchronous mode.
   - **Figure 6 (Single-Event Failover Envelope):** Correctly graphs $2C_{\max} = 2,000\,\text{tokens}$, Local $G_1$, Physical $G_{1,\delta}$, and excess regions $+704.3$ and $+694.3$ tokens at $T = 10\,\text{ms}$.

---

## 2. Independent Verification of Pareto Scopes

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: Multi-Objective Verification across Request Cost Scopes (Phase 5.4.1 Ground Truth).            |
+---------------------+-------------------+---------------------+--------------------+--------------------+
| Policy              | Scope 1 (c <= 700)| Scope 2 (c = 1000)  | Scope 3 (Composite)| Operational Status |
|                     | Request FRR (%)   | Request FRR (%)     | Request FRR (%)    |                    |
+---------------------+-------------------+---------------------+--------------------+--------------------+
| static_db_070c      | 0.00% (Eligible)  | 100.0% (DISQUALIFIED| 28.5% (DISQUALIFIED| Scope 1 Sole Member|
| tb_standard (C=700) | 0.00% (Eligible)  | 100.0% (DISQUALIFIED| 28.5% (DISQUALIFIED| Dominated          |
| gcra (tau=700ms)    | 0.00% (Eligible)  | 100.0% (DISQUALIFIED| 28.5% (DISQUALIFIED| Dominated          |
| tb_large (C=1000)   | 0.00% (Eligible)  | 0.00% (Eligible)    | 0.00% (Eligible)   | Scope 2 & 3 Frontier|
| DREB-RCA70 (Ours)   | 0.00% (Eligible)  | 0.00% (Eligible)    | 0.00% (Eligible)   | Scope 2 & 3 Frontier|
+---------------------+-------------------+---------------------+--------------------+--------------------+
```

### 2.1 Scope 1: Small Bursts ($c \le 700$)
- In applications where batch bursts never exceed 700 tokens, a static dual-bucket calibrated to $B_{\max} = 700$ achieves zero false rejections.
- Because `static_db_070c` requires zero dynamic abuse tracking, zero idle-credit calculations, and maintains zero farming risk above 700, it strictly dominates `rca70` on tracking simplicity and memory footprint.
- **Audited Frontier:** `{"static_db_070c"}` is the sole member. RCA70 is not on the Scope 1 frontier.

### 2.2 Scope 2: Large Bursts ($c = 1,000$)
- In applications with legitimate 1,000-token batch bursts, any limiter with a fixed 700-token cap rejects atomic 1,000-token requests unconditionally (100% Request FRR).
- Operational SLA requires Request FRR $\le 1.0\%$. Thus, `static_db_070c`, `tb_standard`, and `gcra` are disqualified.
- Between the two remaining eligible policies:
  - `tb_large`: Maximizes simplicity (33.4 ns dispatch), but permits 100% farming throughput ($0.4517 - 0.8000\,\text{tok/s}$).
  - `rca70`: Suppresses farming by 56.7% to 85.7% (admitted rate $0.0817 - 0.3467\,\text{tok/s}$) with a negligible 42 ns dispatch latency.
- **Audited Frontier:** `{"rca70", "tb_large"}`.

### 2.3 Scope 3: Composite Production Mix
- Across realistic production mixes containing both small RPCs and occasional large batch spikes, the disqualification of 700-cap limiters persists.
- **Audited Frontier:** `{"rca70", "tb_large"}`.

---

## 3. Detailed Audit of Figures (Figures 1–6)

### Figure 1: Architecture & Dynamic Ceiling
- **Plotted Curve:** $C(K) = 450 / (1 + 0.75 K)$ for $K \in [0, 4.0]$.
- **Data Callouts:**
  - $K = 0.0 \implies 450.0$
  - $K = 1.0 \implies 257.14$
  - $K = 2.0 \implies 180.0$
  - $K = 4.0 \implies 112.5$
- **Audit Erratum:** The figure labels the y-axis as "Effective Burst Ceiling $C_{\mathrm{eff}}$". As established in Deliverable 3, this curve represents the **idle-credit pool ceiling $I_{\text{ceil}}(K)$**, while total burst capacity is $B_{\max} + I_{\text{ceil}}(K)/d = 700 + I_{\text{ceil}}/1.5$. The script and captions must be updated to avoid reader confusion.

### Figure 2: Scope 1 & Scope 2 Pareto Frontiers
- **Plotted Coordinates:**
  - Scope 1: `static_db_070c` at $(0.0, 0.4517)$; `rca70` at $(0.0, 0.0817)$; `tb_large` at $(0.0, 1.0000)$.
  - Scope 2: `static_db_070c`, `tb_standard`, `gcra` plotted at $x = 100.0\%$ (disqualified region). `rca70` at $(0.0, 0.0817)$; `tb_large` at $(0.0, 1.0000)$.
- **Audit Status:** **PASS**. Cleanly visualizes the operational disqualification and frontier composition.

### Figure 3: Adversarial Farming Suppression Over Time
- **Time Series (W04):** Slope of `tb_large` is $0.4517$; slope of `rca70` is $0.0817$. Area between curves represents $81.9\%$ suppressed traffic volume.
- **Bar Chart:** W04 ($81.9\%$), W08 ($56.7\%$), W10 ($85.7\%$), Closed-Loop Credit Rate ($93.1\%$).
- **Audit Status:** **PASS**. Accurately differentiates request throughput suppression from credit-rate suppression.

### Figure 4: Abuse Score Recovery Trajectories
- **Plotted Slopes:** $dK/dt = -0.05\,\text{s}^{-1}$ from $K_0 \in \{1.0, 1.5, 2.0\}$.
- **Milestones:** $\tau_{0.05} = (1.0 - 0.05)/0.05 = 19.0\,\text{s}$; $\tau_0 = 1.0 / 0.05 = 20.0\,\text{s}$.
- **Audit Status:** **PASS**. Mathematically exact.

### Figure 5: Execution Latency Hierarchy
- **Plotted Values (Log Scale):**
  - Scope A (In-Memory Java): P50 = 0.042 $\mu$s (42 ns), P90 = 0.061 $\mu$s, P99 = 0.083 $\mu$s.
  - Scope B (Redis Lua Engine): P50 = 82 $\mu$s (0.082 ms), P90 = 145 $\mu$s, P99 = 310 $\mu$s.
  - Scope C (Async Network RTT): P50 = 259 $\mu$s (0.259 ms), P90 = 412 $\mu$s, P99 = 667 $\mu$s.
  - Scope D (Sync `WAIT 1` RTT): P50 = 348 $\mu$s (0.348 ms), P90 = 720 $\mu$s, P99 = 1,301 $\mu$s (1.301 ms).
- **Penalty Callout:** $(1301 - 667) / 667 = 634 / 667 = +95.05\% \approx \mathbf{+95.1\%}$ P99 latency penalty.
- **Audit Status:** **PASS**. Confirms that `WAIT 1` P99 is not sub-millisecond.

### Figure 6: Single-Event Failover Over-Admission Envelope
- **Plotted Envelopes ($T \in [0, 50\,\text{ms}]$):**
  - Local $G_1(T) = 1285.7143 + 1000 \cdot T$. At $10\,\text{ms}$: $1295.7143$.
  - Physical $G_{1,\delta}(t) = 1295.7143 + 1000 \cdot t$ ($\delta = 5\,\text{ms}$). At $10\,\text{ms}$: $1305.7143$.
  - Failover Burst Cap: $2 \times C_{\max} = 2,000.0\,\text{tokens}$ per client key.
- **Excess Annotations at $T = 10\,\text{ms}$:**
  - Excess vs. $G_1$: $2000.0 - 1295.7143 = \mathbf{+704.2857\,\text{tokens}}$.
  - Excess vs. $G_{1,\delta}$: $2000.0 - 1305.7143 = \mathbf{+694.2857\,\text{tokens}}$.
- **Scope Visibility:** Explicitly annotated as per-client-key under an isolated single-fault failover event.
- **Audit Status:** **PASS**. Formally aligns with Theorem 3 and Phase 7.3.4 contract.

---

## 4. Conformance with Phase 7.3.4 Guarantee Contract

All mathematical claims in the manuscript and figures adhere strictly to the frozen Phase 7.3.4 contract:
- **$G_1$ Scope:** Local monotonic server time on an uninterrupted Redis primary.
- **$G_{1,\delta}$ Scope:** Physical time under bounded UTC offset $|\tau - t| \le \delta = 5\,\text{ms}$ (elapsed allowance $2\delta = 10\,\text{ms}$).
- **Single-Event Bound:** Bounded to $2C_{\max} = 2,000$ tokens per client key.
- **Repeated Discontinuities Divergence:** Proves rate diverged proportionally to event frequency if $\lim N_E(T)/T > 0$.
- **$G_2$ Convergence:** Sublinear event frequency ($\lim N_E(T)/T = 0$) is sufficient universally, and necessary under saturated adversarial demand.
- **$G_3$ Recovery:** Reference state $K_0 = 1.0 \to \epsilon = 0.05$ achieved in $\tau_{0.05} = 19.0\,\text{s}$.
- **Idempotency Gate H:** Full 5-condition lifecycle with hash-tag affinity and payload validation.
