# Phase 5 — Baseline Comparison and Statistical Evaluation

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`9ecbfc9`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Primary Evaluated Policy:** DREB-RCA70  
**Baseline Comparators:** DREB-AC BM, DREB v2 Default, Static Dual-Bucket (0.70C), Token Bucket (C, R)  
**Raw Dataset:** [`phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv) (1,501 rows)  
**Number of Replications:** 30 paired deterministic seeds ($5000 \dots 5029$) per cell ($N=1,500$ runs)  
**Simulation Horizon:** $T = 60.0\,\text{s}$ per run

---

## 1. Executive Summary

This report delivers a head-to-head empirical comparison of **DREB-RCA70** against four baseline rate-limiting architectures. All comparisons use strictly paired workloads generated from identical random seeds.

### Key Conclusions
1. **Legitimate Burst Preservation (Workload W02):**
   RCA70 achieves **$0.00\%$ FRR** and **$100.00\%$ burst acceptance** on compliant $c=700$ bursts. DREB-AC BM fails severely, suffering **$64.10\%$ false rejection** due to its restricted base burst capacity ($B_{\max} = 428.57$).
2. **Post-Attack Recovery & Starvation Immunity (Workload W10):**
   Following a 30-second farming attack, legitimate clients experience **$0.00\%$ FRR** under RCA70. In contrast, DREB-AC BM exhibits **$60.00\%$ false rejection** because the lingering penalty $K(t)$ discounts idle credit, causing legitimate burst starvation.
3. **Credit Farming Suppression (Workloads W05, W07, W08):**
   RCA70 bounds credit-funded throughput to **$0.0136R - 0.0667R$** across all farming attacks. Baseline DREB v2 leaks up to **$0.3281R$ credit** ($4.9\times$ to $24.1\times$ higher).
4. **Protection Against Burst Preemption in Mixed Traffic (Workload W04):**
   Under concurrent adversarial bursts, legitimate background Poisson traffic suffers **$2.91\% \pm 0.61\%$ FRR under Token Bucket**, compared to only **$0.22\% \pm 0.09\%$ under RCA70**.
5. **Runtime Overhead:**
   RCA70 executes in **$84.0\,\text{ns}$ P50** and **$292.0\,\text{ns}$ P99**, well within microsecond-scale SLA budgets for production API gateways.

---

## 2. Comprehensive Workload Comparison Table

Values are presented as $\text{Mean} \pm \text{Standard Deviation}$ across 30 paired seeds. Paired differences against RCA70 are computed as $\Delta = \text{Policy} - \text{RCA70}$ with $95\%$ confidence intervals.

| Workload | Policy | Total Rate ($r_{\text{tot}}$) | Adv Rate ($r_{\text{adv}}$) | Credit Rate ($r_{\text{cred}}$) | Legit FRR (%) | Burst Acc (%) | Paired $\Delta \text{FRR}$ vs RCA70 | Paired $\Delta \text{Credit}$ vs RCA70 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **W01: Benign Steady** | **RCA70** | $0.9803 \pm 0.0041$ | $0.0000$ | $0.0000$ | **$0.00\%$** | N/A | — | — |
| | DREB-AC BM | $0.9803 \pm 0.0041$ | $0.0000$ | $0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| | DREB v2 | $0.9803 \pm 0.0041$ | $0.0000$ | $0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| | Static DB | $0.9803 \pm 0.0041$ | $0.0000$ | $0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| | Token Bucket | $0.9803 \pm 0.0041$ | $0.0000$ | $0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| **W02: On/Off Bursts** | **RCA70** | $0.4550 \pm 0.0000$ | $0.0000$ | $0.0000$ | **$0.00\%$** | **$100.00\%$** | — | — |
| | DREB-AC BM | $0.1633 \pm 0.0000$ | $0.0000$ | $0.0633$ | **$64.10\%$** | **$35.90\%$** | **$+64.10\% \pm 0.00\%$** | $+0.0633 \pm 0.0000$ |
| | DREB v2 | $0.4433 \pm 0.0000$ | $0.0000$ | $0.1719$ | **$2.56\%$** | **$97.44\%$** | $+2.56\% \pm 0.00\%$ | $+0.1719 \pm 0.0000$ |
| | Static DB | $0.4550 \pm 0.0000$ | $0.0000$ | $0.0000$ | $0.00\%$ | $100.00\%$ | $+0.00\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| | Token Bucket | $0.4550 \pm 0.0000$ | $0.0000$ | $0.0000$ | $0.00\%$ | $100.00\%$ | $+0.00\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| **W03: Overload (1.5R)**| **RCA70** | $1.0212 \pm 0.0000$ | $0.0000$ | $0.0000$ | $31.92\%$ | N/A | — | — |
| | DREB-AC BM | $1.0212 \pm 0.0000$ | $0.0000$ | $0.0000$ | $31.92\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| | DREB v2 | $1.0212 \pm 0.0000$ | $0.0000$ | $0.0000$ | $31.92\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| | Static DB | $1.0212 \pm 0.0000$ | $0.0000$ | $0.0000$ | $31.92\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| | Token Bucket | $1.0163 \pm 0.0000$ | $0.0000$ | $0.0000$ | $32.25\%$ | N/A | $+0.33\% \pm 0.00\%$ | $+0.0000 \pm 0.0000$ |
| **W04: Mixed Competing**| **RCA70** | $0.3339 \pm 0.0162$ | $0.0817 \pm 0.0051$ | $0.0245 \pm 0.0015$ | **$0.22\% \pm 0.09\%$** | N/A | — | — |
| | DREB-AC BM | $0.3310 \pm 0.0178$ | $0.0789 \pm 0.0075$ | $0.0451 \pm 0.0043$ | $0.28\% \pm 0.11\%$ | N/A | $+0.06\% \pm 0.03\%$ | $+0.0206 \pm 0.0013$ |
| | DREB v2 | $0.5458 \pm 0.0201$ | $0.2950 \pm 0.0153$ | $0.1686 \pm 0.0087$ | $0.81\% \pm 0.23\%$ | N/A | $+0.59\% \pm 0.15\%$ | **$+0.1441 \pm 0.0031$** |
| | Static DB | $0.2528 \pm 0.0151$ | $0.0000 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\% \pm 0.00\%$ | N/A | $-0.22\% \pm 0.09\%$ | $-0.0245 \pm 0.0015$ |
| | Token Bucket | $0.6971 \pm 0.0414$ | $0.4517 \pm 0.0482$ | $0.0000 \pm 0.0000$ | **$2.91\% \pm 0.61\%$** | N/A | **$+2.69\% \pm 0.58\%$** | $-0.0245 \pm 0.0015$ |
| **W05: Timing Farming**| **RCA70** | $1.0000 \pm 0.0000$ | $1.0000 \pm 0.0000$ | **$0.0667 \pm 0.0000$** | $0.00\%$ | N/A | — | — |
| | DREB-AC BM | $0.1375 \pm 0.0000$ | $0.1375 \pm 0.0000$ | $0.0589 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0077 \pm 0.0000$ |
| | DREB v2 | $0.7500 \pm 0.0000$ | $0.7500 \pm 0.0000$ | **$0.3214 \pm 0.0000$** | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | **$+0.2548 \pm 0.0000$** |
| | Static DB | $0.0000 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0667 \pm 0.0000$ |
| | Token Bucket | $0.8000 \pm 0.0000$ | $0.8000 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0667 \pm 0.0000$ |
| **W06: White-Box** | **RCA70** | $0.9837 \pm 0.0000$ | $0.9837 \pm 0.0000$ | **$0.0136 \pm 0.0000$** | $0.00\%$ | N/A | — | — |
| | DREB-AC BM | $0.8836 \pm 0.0000$ | $0.8836 \pm 0.0000$ | $0.0458 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0322 \pm 0.0000$ |
| | DREB v2 | $0.9774 \pm 0.0000$ | $0.9774 \pm 0.0000$ | **$0.1387 \pm 0.0000$** | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | **$+0.1251 \pm 0.0000$** |
| | Static DB | $0.7000 \pm 0.0000$ | $0.7000 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0136 \pm 0.0000$ |
| | Token Bucket | $0.9917 \pm 0.0000$ | $0.9917 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0136 \pm 0.0000$ |
| **W07: Black-Box** | **RCA70** | $0.9736 \pm 0.0000$ | $0.9736 \pm 0.0000$ | **$0.0433 \pm 0.0000$** | $0.00\%$ | N/A | — | — |
| | DREB-AC BM | $0.6955 \pm 0.0000$ | $0.6955 \pm 0.0000$ | $0.1072 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0640 \pm 0.0000$ |
| | DREB v2 | $0.8725 \pm 0.0000$ | $0.8725 \pm 0.0000$ | **$0.2741 \pm 0.0000$** | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | **$+0.2308 \pm 0.0000$** |
| | Static DB | $0.8266 \pm 0.0000$ | $0.8266 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0433 \pm 0.0000$ |
| | Token Bucket | $0.9019 \pm 0.0000$ | $0.9019 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0433 \pm 0.0000$ |
| **W08: AIMD Adaptive**| **RCA70** | $0.3467 \pm 0.0000$ | $0.3467 \pm 0.0000$ | **$0.0433 \pm 0.0000$** | $0.00\%$ | N/A | — | — |
| | DREB-AC BM | $0.1200 \pm 0.0000$ | $0.1200 \pm 0.0000$ | $0.0557 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.0124 \pm 0.0000$ |
| | DREB v2 | $0.7067 \pm 0.0000$ | $0.7067 \pm 0.0000$ | **$0.3281 \pm 0.0000$** | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | **$+0.2848 \pm 0.0000$** |
| | Static DB | $0.0000 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0433 \pm 0.0000$ |
| | Token Bucket | $0.8000 \pm 0.0000$ | $0.8000 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0433 \pm 0.0000$ |
| **W09: Multi-Worker** | **RCA70** | $1.0039 \pm 0.0078$ | $1.0039 \pm 0.0078$ | **$0.0144 \pm 0.0101$** | $0.00\%$ | N/A | — | — |
| | DREB-AC BM | $0.9517 \pm 0.0566$ | $0.9517 \pm 0.0566$ | $0.1397 \pm 0.0079$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.1253 \pm 0.0060$ |
| | DREB v2 | $0.9956 \pm 0.0104$ | $0.9956 \pm 0.0104$ | $0.1849 \pm 0.0214$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $+0.1705 \pm 0.0068$ |
| | Static DB | $0.9928 \pm 0.0324$ | $0.9928 \pm 0.0324$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0144 \pm 0.0036$ |
| | Token Bucket | $0.9978 \pm 0.0037$ | $0.9978 \pm 0.0037$ | $0.0000 \pm 0.0000$ | $0.00\%$ | N/A | $+0.00\% \pm 0.00\%$ | $-0.0144 \pm 0.0036$ |
| **W10: Recovery** | **RCA70** | $0.2833 \pm 0.0000$ | $0.0500 \pm 0.0000$ | **$0.0150 \pm 0.0000$** | **$0.00\%$** | **$100.00\%$** | — | — |
| | DREB-AC BM | $0.1433 \pm 0.0000$ | $0.0500 \pm 0.0000$ | $0.0648 \pm 0.0000$ | **$60.00\%$** | **$40.00\%$** | **$+60.00\% \pm 0.00\%$** | $+0.0498 \pm 0.0000$ |
| | DREB v2 | $0.4500 \pm 0.0000$ | $0.2167 \pm 0.0000$ | $0.2143 \pm 0.0000$ | $0.00\%$ | $100.00\%$ | $+0.00\% \pm 0.00\%$ | $+0.1993 \pm 0.0000$ |
| | Static DB | $0.2333 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | $100.00\%$ | $+0.00\% \pm 0.00\%$ | $-0.0150 \pm 0.0000$ |
| | Token Bucket | $0.5833 \pm 0.0000$ | $0.3500 \pm 0.0000$ | $0.0000 \pm 0.0000$ | $0.00\%$ | $100.00\%$ | $+0.00\% \pm 0.00\%$ | $-0.0150 \pm 0.0000$ |

---

## 3. Operational Profiling and Runtime Benchmarks

Each policy was profiled over 50,000 requests following 20,000 warmup requests to capture the true latency distribution:

| Policy ID | Policy Name | P50 Latency (ns) | P95 Latency (ns) | P99 Latency (ns) | Memory per State (Bytes) | Distributed Storage Complexity |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`rca70`** | **DREB-RCA70** | **$84.0\,\text{ns}$** | **$250.0\,\text{ns}$** | **$292.0\,\text{ns}$** | $40\,\text{bytes}$ ($4 \times \text{f64} + 1 \times \text{u64}$) | Atomic Lua script (4 Redis hash fields) |
| **`dreb_ac_bm`** | DREB-AC BM | $125.0\,\text{ns}$ | $167.0\,\text{ns}$ | $250.0\,\text{ns}$ | $40\,\text{bytes}$ ($4 \times \text{f64} + 1 \times \text{u64}$) | Atomic Lua script (4 Redis hash fields) |
| **`dreb_v2_default`**| DREB v2 Default | $41.0\,\text{ns}$ | $208.0\,\text{ns}$ | $209.0\,\text{ns}$ | $48\,\text{bytes}$ ($5 \times \text{f64} + 1 \times \text{u64}$) | Atomic Lua script (5 Redis hash fields) |
| **`static_db_070c`** | Static DB (0.70C) | $83.0\,\text{ns}$ | $208.0\,\text{ns}$ | $209.0\,\text{ns}$ | $24\,\text{bytes}$ ($2 \times \text{f64} + 1 \times \text{u64}$) | Atomic Lua script (2 Redis hash fields) |
| **`tb_large`** | Token Bucket | $42.0\,\text{ns}$ | $209.0\,\text{ns}$ | $334.0\,\text{ns}$ | $16\,\text{bytes}$ ($1 \times \text{f64} + 1 \times \text{u64}$) | Redis single-key decrement / Lua script |

### Operational Analysis
- **CPU / Execution Time:** DREB-RCA70 adds approximately $42\,\text{ns}$ P50 overhead compared to classical Token Bucket due to the secondary sustained bucket and exponential decay arithmetic. In absolute terms, $84.0\,\text{ns}$ corresponds to $> 11.9\,\text{million decisions per second}$ on a single core, which is negligible in modern network stacks.
- **State Footprint:** RCA70 requires 40 bytes of state per client, allowing 1 million active client limiters to fit in under $40\,\text{MB}$ of memory.
