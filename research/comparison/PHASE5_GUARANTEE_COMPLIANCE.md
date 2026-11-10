# Phase 5 — Guarantee G1 Compliance and Finite-Window Rate Audit

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`9ecbfc9`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Policies:** DREB-RCA70, DREB-AC BM, DREB v2 Default, Static Dual-Bucket (0.70C), Token Bucket (C, R)  
**Dataset:** [`phase5_baseline_comparison_raw.csv`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/comparison/phase5_baseline_comparison_raw.csv) (1,501 rows)

---

## 1. Executive Objective

A central mandate of Phase 5 is to resolve an apparent inconsistency identified in earlier project phases:
> *Why did experiments in Phase 2 and Phase 4 observe normalized admission rates of approximately $1.02R$ under heavy overload (e.g. $1.50R$ offered load), and does an average rate exceeding $1.000R$ over a finite measurement window constitute a violation of Guarantee G1?*

This document provides the definitive mathematical derivation, formal definitions, and empirical audit proving that:
1. **An average rate above $R$ over a finite observation window $T$ is not a violation of Guarantee G1.**
2. The observed rate of $1.0214R$ is the exact theoretical upper bound permitted by the initial burst allowance $S_{\max}$ over horizon $T = 60.0\,\text{s}$.
3. Across all 1,500 experimental runs in Phase 5, **zero G1 envelope violations occurred** ($Viol(T) = 0.00$).

---

## 2. Mathematical Formulation: Envelope vs. Finite-Window Rate

### 2.1 Formal Definition of Guarantee G1 (Cumulative Envelope)
Guarantee G1 governs the total cumulative tokens admitted from a client identity over any time interval $[0, T]$:
$$A(T) \le E_{\text{G1}}(T) \equiv S_{\max} + R \cdot T$$
where:
- $S_{\max}$: Maximum capacity of the sustained bucket ($S_{\max} = \frac{9}{7}C \approx 1285.71\,\text{tokens}$ for DREB).
- $R$: Contracted sustained refill rate ($R = 1000\,\text{tokens/s}$).
- $E_{\text{G1}}(T)$: The absolute upper bound envelope on cumulative admissions.

### 2.2 Finite-Window Average Rate Formulation
The normalized average admitted rate over measurement window $T$ is defined as:
$$r_{\text{avg}}(T) \equiv \frac{A(T)}{R \cdot T}$$

Substituting the cumulative bound $A(T) \le S_{\max} + R \cdot T$ into the rate equation yields:
$$r_{\text{avg}}(T) \le \frac{S_{\max} + R \cdot T}{R \cdot T} = 1.0 + \frac{S_{\max}}{R \cdot T}$$

### 2.3 The Initial Burst Allowance Contribution
The term $\frac{S_{\max}}{R \cdot T}$ represents the **finite-window transient contribution** of the initial bucket balance:
$$\Delta r_{\text{init}}(T) = \frac{S_{\max}}{R \cdot T}$$

For the evaluated benchmark parameters ($C = 1000, R = 1000\,\text{tokens/s}, S_{\max} = 1285.71, T = 60.0\,\text{s}$):
$$\Delta r_{\text{init}}(60\,\text{s}) = \frac{1285.71}{1000 \times 60} = \frac{1285.71}{60000} \approx \mathbf{0.0214285} \quad (+2.143\%)$$

Therefore, under maximal offered load, the maximum permitted normalized average rate over $T = 60.0\,\text{s}$ is:
$$r_{\text{max\_permitted}}(60\,\text{s}) = 1.0 + 0.02143 = \mathbf{1.02143 R}$$

### 2.4 Asymptotic Convergence to Sustained Rate
As the measurement window $T \to \infty$, the initial burst allowance contribution decays to zero:
$$\lim_{T \to \infty} r_{\text{avg}}(T) \le \lim_{T \to \infty} \left( 1.0 + \frac{S_{\max}}{R \cdot T} \right) = \mathbf{1.000 R}$$

| Measurement Horizon ($T$) | $S_{\max}$ Tokens | Sustained Tokens ($R \cdot T$) | Max Admitted $A(T)$ | Max Average Rate ($r_{\text{avg}}$) |
| :--- | :--- | :--- | :--- | :--- |
| $T = 1.0\,\text{s}$ | $1285.71$ | $1,000$ | $2,285.71$ | $2.2857 R$ |
| $T = 10.0\,\text{s}$ | $1285.71$ | $10,000$ | $11,285.71$ | $1.1286 R$ |
| **$T = 60.0\,\text{s}$ (Benchmark)** | **$1285.71$** | **$60,000$** | **$61,285.71$** | **$1.0214 R$** |
| $T = 300.0\,\text{s}$ (5 min) | $1285.71$ | $300,000$ | $301,285.71$ | $1.0043 R$ |
| $T = 3600.0\,\text{s}$ (1 hour) | $1285.71$ | $3,600,000$ | $3,601,285.71$ | $1.0004 R$ |

> [!IMPORTANT]
> **Resolution of Throughput Invariant:**
> An empirical rate of $1.0212R - 1.0214R$ observed over a 60-second test is **mathematically compliant with Guarantee G1**. It reflects the lawful depletion of the initial burst allowance $S_{\max}$ alongside the steady sustained refill rate $R$.

---

## 3. Empirical Audit Across All Benchmark Workloads

The table below presents the cumulative admissions $A(T)$, G1 envelope $E_{\text{G1}}(T)$, envelope slack, and envelope violations across all 10 workloads and all 5 policies ($N=30$ seeds each):

| Workload | Policy | Admitted Tokens $A(T)$ | Normalized Rate $r_{\text{avg}}$ | G1 Envelope $E_{\text{G1}}(T)$ | Envelope Slack ($Slack(T)$) | Envelope Violations ($Viol(T)$) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **W01: Benign Steady** | **RCA70** | $58,817 \pm 247$ | $0.9803 R$ | $61,285.71$ | $+2,468.6$ | **$0.00$** |
| | Token Bucket | $58,817 \pm 247$ | $0.9803 R$ | $61,000.00$ | $+2,182.9$ | **$0.00$** |
| **W02: On/Off Bursts** | **RCA70** | $27,300 \pm 0$ | $0.4550 R$ | $61,285.71$ | $+33,985.7$ | **$0.00$** |
| | DREB-AC BM | $9,800 \pm 0$ | $0.1633 R$ | $61,285.71$ | $+51,485.7$ | **$0.00$** |
| **W03: Overload (1.5R)**| **RCA70** | **$61,275 \pm 0$** | **$1.0212 R$** | **$61,285.71$** | **$+10.71$** | **$0.00$** |
| | DREB-AC BM | $61,275 \pm 0$ | $1.0212 R$ | $61,285.71$ | $+10.71$ | **$0.00$** |
| | DREB v2 | $61,275 \pm 0$ | $1.0212 R$ | $61,285.71$ | $+10.71$ | **$0.00$** |
| | Static DB | $61,275 \pm 0$ | $1.0212 R$ | $61,285.71$ | $+10.71$ | **$0.00$** |
| | Token Bucket | $60,975 \pm 0$ | $1.0163 R$ | $61,000.00$ | $+25.00$ | **$0.00$** |
| **W04: Mixed Competing**| **RCA70** | $20,035 \pm 973$ | $0.3339 R$ | $61,285.71$ | $+41,250.7$ | **$0.00$** |
| | Token Bucket | $41,828 \pm 2484$ | $0.6971 R$ | $61,000.00$ | $+19,171.7$ | **$0.00$** |
| **W05: Timing Farming**| **RCA70** | $60,000 \pm 0$ | $1.0000 R$ | $61,285.71$ | $+1,285.7$ | **$0.00$** |
| | DREB v2 | $45,000 \pm 0$ | $0.7500 R$ | $61,285.71$ | $+16,285.7$ | **$0.00$** |
| **W06: White-Box** | **RCA70** | $59,021 \pm 0$ | $0.9837 R$ | $61,285.71$ | $+2,264.7$ | **$0.00$** |
| | Token Bucket | $59,500 \pm 0$ | $0.9917 R$ | $61,000.00$ | $+1,500.0$ | **$0.00$** |
| **W07: Black-Box** | **RCA70** | $58,416 \pm 0$ | $0.9736 R$ | $61,285.71$ | $+2,869.7$ | **$0.00$** |
| **W08: AIMD Adaptive**| **RCA70** | $20,800 \pm 0$ | $0.3467 R$ | $61,285.71$ | $+40,485.7$ | **$0.00$** |
| | Token Bucket | $48,000 \pm 0$ | $0.8000 R$ | $61,000.00$ | $+13,000.0$ | **$0.00$** |
| **W09: Multi-Worker** | **RCA70** | $60,233 \pm 468$ | $1.0039 R$ | $61,285.71$ | $+1,052.4$ | **$0.00$** |
| **W10: Recovery** | **RCA70** | $17,000 \pm 0$ | $0.2833 R$ | $61,285.71$ | $+44,285.7$ | **$0.00$** |
| | DREB-AC BM | $8,600 \pm 0$ | $0.1433 R$ | $61,285.71$ | $+52,685.7$ | **$0.00$** |

---

## 4. Key Audit Findings

1. **Strict Non-Negative Slack:** Across all 1,500 runs, $Slack(T) > 0$. The minimum slack observed across the entire benchmark suite was **$+10.71$ tokens** under W03 sustained overload, corresponding to $99.982\%$ envelope utilization without ever exceeding the bound.
2. **Zero Violations:** In no run did $A(T)$ exceed $E_{\text{G1}}(T)$. Guarantee G1 is preserved unconditionally across all tested policies and workloads.
3. **Comparative Accounting Integrity:** Token Bucket's envelope is $C + R \cdot T = 61,000$ tokens, whereas DREB's envelope is $S_{\max} + R \cdot T = 61,285.71$ tokens. Both policies adhere strictly to their respective formal bounds.
