# Phase 5.4.1 — Scope 1 Operational Pareto Verification & Latency Reconciliation

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`62a3774`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Scope:** Scope 1 (Constrained Bursts, $c \le 700$)  
**Eligible Candidates:** Static Dual-Bucket (0.70C), Token Bucket (C, R), DREB-RCA70, DREB v2 Default  
**Raw Source Dataset:** [`phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv) (1,501 rows, 30 seeds)  
**Verification Test Suite:** [`Phase541RequestSemanticsTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase541RequestSemanticsTest.java)

---

## 1. Executive Summary

This report delivers the independent verification of the **Scope 1 Operational Pareto Frontier** using complete, workload-matched, six-dimensional objective vectors. It specifically resolves:
1. **Workload-Matched Vector Reconstruction:** Constructing objective vectors strictly from workloads conforming to the $c \le 700$ constraint.
2. **Latency Reconciliation:** Resolving the reported Token Bucket latency of $42\,\text{ns}$ versus Static Dual-Bucket latency of $83\,\text{ns}$ as a percentile mismatch (P50 vs. P99).
3. **Rigorous Dominance Verification:** Proving whether Static Dual-Bucket's claimed dominance over Token Bucket, RCA70, and DREB v2 survives under matched statistics and Scope-1 compliant attack workloads.

---

## 2. Workload Definition & Taxonomy for Scope 1 ($c \le 700$)

Scope 1 models environments where legitimate clients contract burst allowances no greater than $c = 700$ tokens ($0.70C$).

### 2.1 Included Workloads & Cost Ranges
To prevent cross-scope contamination, the evaluation identifies the exact request costs across all Phase 5 workloads:
- **W01 (Benign Steady):** Poisson arrivals, average cost $\approx 49\,\text{tokens} \le 700$. (All policies achieve $0.00\%$ FRR).
- **W02 (Legitimate ON/OFF Burst):** Periodic bursts of cost $c = 700$ every $1.5\,\text{s}$. (Used for $\text{Obj}_1$ and $\text{Obj}_2$).
- **W03 (Sustained Overload):** Continuous requests of cost $c = 15 \le 700$. (Evaluates uncontracted excess shedding).
- **W09 (Multi-Worker Collision):** 4 unsynchronized adversarial workers sending bursts of cost $c = 500 \le 700$ every $1.0\,\text{s}$. (This is the **pure Scope-1 compliant adversarial workload**, where attack bursts strictly respect $c \le 700$).
- **W10 (Post-Attack Recovery):** Legitimate bursts of cost $c = 700$ in $[30\,\text{s}, 60\,\text{s}]$ following adversarial saturation. (Used for $\text{Obj}_3$).

*Note on Non-Scope-1 Workloads:* In W04 ($c_{\text{adv}}=1000$), W05 ($c_{\text{adv}}=750$), and W08 ($c_{\text{adv}}=800$), the adversary issued requests exceeding $700$. While Static DB trivially blocks them ($0.000R$ admission) due to $B_{\max}=700$, evaluating W09 ($c=500$) tests whether Static DB maintains its advantage when the attack cost is strictly within the $c \le 700$ budget.

---

## 3. Statistical Latency Reconciliation: 42 ns vs. 83 ns

The independent review raised a vital question: *If Token Bucket achieved $42\,\text{ns}$ latency in earlier phases while Static DB was reported at $83\,\text{ns}$, does Token Bucket outperform Static DB on latency?*

### 3.1 Empirical Latency Percentile Distribution
An audit of `phase5_baseline_comparison_raw.csv` and the microbenchmark in `Phase5BaselineComparisonTest.java` (50,000 timed requests per policy using `System.nanoTime()`) reveals the complete percentile distribution:

| Policy ID | Policy Name | P50 (Median) | P95 | P99 | Mach Tick Equivalent (41.67 ns / tick) |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **`static_db_070c`** | Static Dual-Bucket (0.70C) | **$42.0\,\text{ns}$** | **$42.0\,\text{ns}$** | **$83.0\,\text{ns}$** | P50 = 1 tick, P95 = 1 tick, P99 = 2 ticks |
| **`tb_large`** | Token Bucket (C, R) | **$42.0\,\text{ns}$** | **$84.0\,\text{ns}$** | **$125.0\,\text{ns}$** | P50 = 1 tick, P95 = 2 ticks, P99 = 3 ticks |
| **`dreb_ac_bm`** | DREB-AC BM | $42.0\,\text{ns}$ | $42.0\,\text{ns}$ | $42.0\,\text{ns}$ | P50 = 1 tick, P95 = 1 tick, P99 = 1 tick |
| **`dreb_v2_default`** | DREB v2 Default | $83.0\,\text{ns}$ | $84.0\,\text{ns}$ | $125.0\,\text{ns}$ | P50 = 2 ticks, P95 = 2 ticks, P99 = 3 ticks |
| **`rca70`** | DREB-RCA70 | $125.0\,\text{ns}$ | $167.0\,\text{ns}$ | $208.0\,\text{ns}$ | P50 = 3 ticks, P95 = 4 ticks, P99 = 5 ticks |

### 3.2 Reconciliation Finding
- **The $42\,\text{ns}$ figure for Token Bucket is its P50 median latency** (1 Mach tick).
- **The $83\,\text{ns}$ figure for Static DB is its P99 tail latency** (2 Mach ticks).
- Comparing Token Bucket's P50 against Static DB's P99 was a cross-percentile mismatch.
- When compared under **matched statistics**:
  - **At P50:** Static DB ($42.0\,\text{ns}$) and Token Bucket ($42.0\,\text{ns}$) are **tied** at 1 Mach tick.
  - **At P95:** Static DB ($42.0\,\text{ns}$) is **strictly faster** than Token Bucket ($84.0\,\text{ns}$).
  - **At P99:** Static DB ($83.0\,\text{ns}$) is **strictly faster** than Token Bucket ($125.0\,\text{ns}$).

Static Dual-Bucket is equal to or faster than Token Bucket across every percentile.

---

## 4. Complete Six-Dimensional Scope 1 Objective Vectors

All six objectives are defined with formal units and optimization directions:
- $\text{Obj}_1$: $\text{FRR}_{\text{burst}}$ on W02 ($c=700$) — Minimize ($\%$).
- $\text{Obj}_2$: $\text{BAR}_{\text{burst}}$ on W02 ($c=700$) — Maximize ($\%$).
- $\text{Obj}_3$: $\text{RecTime}$ on W10 ($c=700$) — Minimize ($\text{s}$).
- $\text{Obj}_4$: $\text{AdvRate}$ on W09 ($c=500 \le 700$) — Minimize ($R$).
- $\text{Obj}_5$: $\text{CreditRate}$ on W09 ($c=500 \le 700$) — Minimize ($R$).
- $\text{Obj}_6$: Decision Latency — Minimize ($\text{ns}$).

### 4.1 Reconstructed Vectors under Matched P99 Tail Latency

| Policy ID | $\text{Obj}_1$: $\text{FRR}_{\text{burst}}$ | $\text{Obj}_2$: $\text{BAR}_{\text{burst}}$ | $\text{Obj}_3$: $\text{RecTime}$ | $\text{Obj}_4$: $\text{AdvRate}_{\text{W09}}$ | $\text{Obj}_5$: $\text{Credit}_{\text{W09}}$ | $\text{Obj}_6$: P99 Latency |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **`static_db_070c`** | $\mathbf{0.00\%}$ | $\mathbf{100.0\%}$ | $\mathbf{1.00\,\text{s}}$ | $\mathbf{0.9928R}$ | $\mathbf{0.0000R}$ | $\mathbf{83\,\text{ns}}$ |
| **`tb_large`** | $0.00\%$ | $100.0\%$ | $1.00\,\text{s}$ | $0.9978R$ | $0.0000R$ | $125\,\text{ns}$ |
| **`rca70`** | $0.00\%$ | $100.0\%$ | $1.00\,\text{s}$ | $1.0039R$ | $0.0144R$ | $208\,\text{ns}$ |
| **`dreb_v2_default`** | $2.56\%$ | $97.4\%$ | $1.00\,\text{s}$ | $0.9956R$ | $0.1849R$ | $125\,\text{ns}$ |

### 4.2 Reconstructed Vectors under Matched P50 Median Latency

| Policy ID | $\text{Obj}_1$: $\text{FRR}_{\text{burst}}$ | $\text{Obj}_2$: $\text{BAR}_{\text{burst}}$ | $\text{Obj}_3$: $\text{RecTime}$ | $\text{Obj}_4$: $\text{AdvRate}_{\text{W09}}$ | $\text{Obj}_5$: $\text{Credit}_{\text{W09}}$ | $\text{Obj}_6$: P50 Latency |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **`static_db_070c`** | $\mathbf{0.00\%}$ | $\mathbf{100.0\%}$ | $\mathbf{1.00\,\text{s}}$ | $\mathbf{0.9928R}$ | $\mathbf{0.0000R}$ | $\mathbf{42\,\text{ns}}$ |
| **`tb_large`** | $0.00\%$ | $100.0\%$ | $1.00\,\text{s}$ | $0.9978R$ | $0.0000R$ | $\mathbf{42\,\text{ns}}$ |
| **`rca70`** | $0.00\%$ | $100.0\%$ | $1.00\,\text{s}$ | $1.0039R$ | $0.0144R$ | $125\,\text{ns}$ |
| **`dreb_v2_default`** | $2.56\%$ | $97.4\%$ | $1.00\,\text{s}$ | $0.9956R$ | $0.1849R$ | $83\,\text{ns}$ |

---

## 5. Pairwise Dominance Analysis

Let $A \succ B$ denote that $A$ is no worse than $B$ across all six objectives and strictly better in at least one.

### 5.1 Static Dual-Bucket vs. Token Bucket
- **$\text{FRR}_{\text{burst}}$:** $0.00\% == 0.00\%$ (Tied)
- **$\text{BAR}_{\text{burst}}$:** $100.0\% == 100.0\%$ (Tied)
- **$\text{RecTime}$:** $1.00\,\text{s} == 1.00\,\text{s}$ (Tied)
- **$\text{AdvRate}$ (W09):** $\mathbf{0.9928R < 0.9978R}$ (**Static DB is strictly superior by $0.0050R$, shedding excess multi-worker burst collisions**)
- **$\text{CreditRate}$:** $0.0000R == 0.0000R$ (Tied)
- **Latency:**
  - At P99: $\mathbf{83\,\text{ns} < 125\,\text{ns}}$ (**Static DB is strictly superior**)
  - At P50: $42\,\text{ns} == 42\,\text{ns}$ (Tied)
- **Conclusion:** Under both matched P99 and matched P50, Static Dual-Bucket is no worse than Token Bucket on all objectives, and strictly better on $\text{AdvRate}$ (and P99 latency).  
  $$\mathbf{\text{Static DB} \succ \text{Token Bucket}}$$

### 5.2 Static Dual-Bucket vs. DREB-RCA70
- **AdvRate (W09):** $\mathbf{0.9928R < 1.0039R}$ (Static DB strictly superior)
- **CreditRate:** $\mathbf{0.0000R < 0.0144R}$ (Static DB strictly superior)
- **Latency:** $\mathbf{83\,\text{ns} < 208\,\text{ns}}$ (P99) / $\mathbf{42\,\text{ns} < 125\,\text{ns}}$ (P50) (Static DB strictly superior)
- **Conclusion:** Static Dual-Bucket strictly dominates RCA70 across multiple dimensions.  
  $$\mathbf{\text{Static DB} \succ \text{RCA70}}$$

### 5.3 Static Dual-Bucket vs. DREB v2 Default
- Static DB has strictly lower $\text{FRR}$ ($0.00\% < 2.56\%$), higher $\text{BAR}$ ($100.0\% > 97.4\%$), lower $\text{AdvRate}$ ($0.9928R < 0.9956R$), lower credit ($0.0000R < 0.1849R$), and lower latency ($83\,\text{ns} < 125\,\text{ns}$).  
  $$\mathbf{\text{Static DB} \succ \text{DREB v2 Default}}$$

---

## 6. Scope 1 Verification Conclusion

The independent audit confirms:
1. When evaluating Scope 1 under matched, statistically sound objective vectors, **Static Dual-Bucket strictly dominates every eligible candidate**.
2. Neither Token Bucket, nor RCA70, nor DREB v2 can enter the Scope 1 operational frontier.
3. Therefore, the Scope 1 Operational Pareto Frontier is mathematically and experimentally confirmed as:
   $$\mathbf{\text{Operational Frontier}_{\text{Scope 1}} = \{\text{Static Dual-Bucket (0.70C)}\}}$$
