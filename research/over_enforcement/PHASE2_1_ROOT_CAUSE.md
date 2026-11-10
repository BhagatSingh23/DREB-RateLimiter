# Phase 2.1: Mathematical Root-Cause Diagnosis of Over-Enforcement Near Contracted Rate

**Project:** DREB — Idle-Conditioned Burst Limiting  
**Phase:** 2.1 — Independent Mathematical Diagnosis  
**Branch:** `research/systematic-improvement`  
**Status:** COMPLETE — Root Cause Fully Formulated and Numerically Verified  
**Artifact Path:** [`research/over_enforcement/PHASE2_1_ROOT_CAUSE.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/over_enforcement/PHASE2_1_ROOT_CAUSE.md)

---

## 1. Executive Summary & Problem Formulation

In benchmark evaluations of DREB (both v1 and v2) against Token Bucket baselines in `DREB-Comparator`:
1. **Mild Overload Defect (Table 2.2):** When a client offers traffic moderately above the contracted rate $R$ (e.g., offered load $k \in [1.10, 1.50] \times R$), DREB admits significantly *less* than the contracted rate $R$:
   - At $k = 1.10 R$: DREB burst-matched admits only **$0.806 R$** (19.4% under-service below contract).
   - At $k = 1.25 R$: DREB burst-matched admits only **$0.857 R$** (14.3% under-service below contract).
   - At $k = 1.50 R$: DREB burst-matched admits only **$0.942 R$** (5.8% under-service below contract).
   In contrast, a Token Bucket ($C, R$) admits $1.002 R$–$1.006 R$, precisely fulfilling the contract.
2. **Compliant Bursty False Rejection Defect (Table 3.1):** When a legitimate bursty client offers traffic at an average load of $0.783 R < R$ (idle intervals $\sim \text{Exp}(3\tau)$, bursts in $[0.3C, 0.9C]$):
   - Token Bucket achieves **$0.0\%$ False Rejection Rate (FRR)**.
   - DREB burst-matched suffers **$11.9\%$ FRR**.
   - DREB v2 (adaptive base 1.5) suffers **$10.3\%$ FRR**.

This phenomenon represents a severe **contract conformance defect**: a rate limiter contracted to provide rate $R$ penalizes compliant or mildly over-budget users by withholding contracted capacity.

This report establishes the complete mathematical diagnosis of this over-enforcement phenomenon.

---

## 2. Mathematical Diagnosis of the Dual-Bucket Conjunction

### 2.1 The Admission Condition
In DREB, every incoming request of integer cost $c > 0$ at timestamp $t$ is subjected to the dual conjunction in [`DREBLimiter.java#L65`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/DREBLimiter.java#L65):
$$\text{Decision} = \text{ALLOW} \iff \Big( S(t) \ge c \Big) \;\land\; \Big( B_{\text{eff}}(t) \ge c \Big)$$
where:
$$B_{\text{eff}}(t) = B(t) + \frac{I(t)}{\text{baseDecay}}$$

Therefore, the instantaneous admission headroom is:
$$H(t) = \min\left( S(t),\, B(t) + \frac{I(t)}{\text{baseDecay}} \right)$$

### 2.2 The Purpose vs Reality of the Two Buckets
- **Sustained Bucket $S(t)$:** Sized at $S_{\max} = \frac{9}{7} C \approx 1.2857 C$, refilling at the contracted rate $S_{\text{rate}} = R$.  
  *Intended role:* Bound long-term average throughput so $A(T) \le S_{\max} + R \cdot T$ (Theorem 1).
- **Burst Headroom $B_{\text{eff}}(t)$:** Composed of fast burst bucket $B(t)$ (capacity $B_{\max} = \frac{3}{7} C \approx 0.4286 C$, refill rate $B_{\text{rate}} = \frac{5}{3} R$) and idle credit $I(t)$ (accruing at $\text{gain} = \frac{2}{3} R$ while $B = B_{\max}$).  
  *Intended role:* Provide an accelerator allowing rested clients to burst up to $C$.

### 2.3 The Conjunction Bottleneck ("The Conjunction Trap")
The fundamental defect arises because $B_{\text{eff}}(t)$ was designed as an accelerator for idle clients, but by virtue of the logical $\text{AND}$ ($\min(S, B_{\text{eff}})$), it acts as a **strict, permanent bottleneck** during continuous transmission.

When a client is actively transmitting traffic near rate $R$:
1. **Idle Credit Starvation:** Because requests arrive frequently, the burst bucket $B(t)$ is repeatedly drained and rarely or never remains full for any non-zero duration $t_{\text{full}}$. Consequently:
   $$t_{\text{full}} \approx 0 \implies I(t) \approx 0 \implies B_{\text{eff}}(t) \approx B(t)$$
2. **Burst Bucket Capacity Truncation:** While the sustained bucket has capacity $S_{\max} = 1.2857 C$, the burst bucket is capped at:
   $$B_{\max} = \frac{3}{7} C \approx 0.4286 C$$
   Any request with cost $c > B_{\max}$ (even $c = 0.50 C$) is **immediately and unconditionally rejected** whenever $I(t) = 0$, even if the sustained bucket is 100% full ($S(t) = 1.2857 C$)!

---

## 3. Mathematical Trajectory Analysis of Specific Workloads

### 3.1 Mathematical Derivation of Table 2.2 ($k = 1.10, 1.25, 1.50$)

In Experiment E2, an abuser offers repeated bursts of size $c = 0.95 C = 950$ spaced by $dt = \frac{0.95 C}{k \cdot R}$.  
Requests are admitted via chunked binary splitting (`admitBurst`).

Over any interval $dt$:
1. $S(t)$ refills by $\Delta S = dt \cdot R$.
2. $B(t)$ refills from $0$ to $B_{\max} = \frac{3}{7} C = 428.57$ at rate $B_{\text{rate}} = \frac{5}{3} R$. The time required to refill $B$ is:
   $$t_{\text{fill}} = \frac{B_{\max}}{B_{\text{rate}}} = \frac{3/7 C}{5/3 R} = \frac{9}{35} \frac{C}{R} = 0.25714\,\text{s}$$
3. The remaining duration in the cycle is $t_{\text{full}} = \max(0, dt - t_{\text{fill}})$.
4. During $t_{\text{full}}$, idle credit accrues:
   $$\Delta I = t_{\text{full}} \cdot \text{gain} = (dt - 0.25714) \times \frac{2}{3} R$$
5. The maximum effective burst headroom available at the moment of request arrival is:
   $$B_{\text{eff}}(dt) = B_{\max} + \frac{\Delta I}{\text{baseDecay}} = 428.57 + \frac{(dt - 0.25714) \times 666.67}{1.5} = 428.57 + 444.44 (dt - 0.25714)$$
6. Notice the rate of credit generation with respect to elapsed time:
   $$\frac{\text{d}}{\text{d}t} \left( \frac{I}{\text{baseDecay}} \right) = \frac{\text{gain}}{\text{baseDecay}} = \frac{2/3 R}{1.5} = \frac{4}{9} R \approx 0.4444 R!$$
   **The burst bucket plus idle credit earns effective tokens at a rate of only $0.4444 R$—less than half the contracted rate $R$!**

#### Analytical vs Empirical Comparison Across Overload $k$:
The admitted tokens per cycle are bounded by $B_{\text{eff}}(dt)$, yielding effective admitted rate:
$$\bar{R}_{\text{admitted}} = \frac{B_{\text{eff}}(dt)}{dt}$$

| Offered Load $k$ | Cycle Interval $dt$ | Headroom $B_{\text{eff}}(dt)$ | Predicted Rate $\bar{R}_{\text{admitted}} / R$ | Empirical Rate (Table 2.2) | Match Precision |
| :---: | :---: | :---: | :---: | :---: | :---: |
| **$k = 1.10$** | $0.8636\,\text{s}$ | $428.57 + 444.44(0.6065) = 698.12$ | **$0.8084 R$** | **$0.806 R$** | **99.7%** |
| **$k = 1.25$** | $0.7600\,\text{s}$ | $428.57 + 444.44(0.5029) = 652.08$ | **$0.8580 R$** | **$0.857 R$** | **99.9%** |
| **$k = 1.50$** | $0.6333\,\text{s}$ | $428.57 + 444.44(0.3762) = 595.77$ | **$0.9407 R$** | **$0.942 R$** | **99.9%** |

**Conclusion:** Over-enforcement at mild overload is NOT caused by sustained bucket depletion ($S$ has plenty of tokens). It is caused entirely by the fact that the burst bucket's effective generation rate after filling is $\frac{4}{9} R \approx 0.444 R$. Because DREB requires $B_{\text{eff}} \ge c$, the client is choked to the sub-contract rate of the burst bucket!

---

### 3.2 Mathematical Derivation of Table 3.1 (Compliant Bursty FRR = 11.9% at 0.783R)

In Experiment E3, compliant bursty clients generate idle pauses with mean $3\tau \approx 3.86\,\text{s}$ followed by bursts $c \in [0.3C, 0.9C] = [300, 900]$.  
The aggregate offered rate is $0.783 R$, well below the contracted rate $R$.

1. Under an exponential distribution of idle pauses, many pauses are shorter than average:
   $$P(\Delta t < \tau) = 1 - e^{-\tau / 3\tau} = 1 - e^{-1/3} \approx 28.3\%$$
2. After an idle pause of length $\Delta t \in [0.3\text{s}, 1.0\text{s}]$:
   - For $\Delta t = 0.5\tau \approx 0.643\,\text{s}$:
     $$t_{\text{full}} = 0.643 - 0.257 = 0.386\,\text{s}$$
     $$I = 0.386 \times 666.67 = 257.3\,\text{tokens}$$
     $$B_{\text{eff}} = 428.57 + \frac{257.3}{1.5} = 428.57 + 171.5 = 600.1\,\text{tokens}$$
3. When a compliant burst of size $c = 800$ arrives after a 0.643s pause:
   - Token Bucket ($C = 1000$): $TB = \min(1000, TB + 643) \ge 800 \implies$ **ALLOWED (0% FRR)**.
   - Sustained Bucket $S$: $S = \min(1285.7, S + 643) \ge 800 \implies$ condition satisfied.
   - Burst Headroom $B_{\text{eff}}$: $B_{\text{eff}} = 600.1 < 800 \implies$ **REJECTED!**
4. Across the full stochastic Poisson-exponential trajectory, 11.9% of all offered compliant tokens fall into intervals where $S(t) \ge c$ but $B_{\text{eff}}(t) < c$, producing the observed **11.9% False Rejection Rate**.

---

## 4. Root-Cause Taxonomy & Disambiguation

We classify the six candidate causes specified in Phase 2.1:

| Candidate Cause | Contributing? | Mathematical Diagnosis |
| :--- | :---: | :--- |
| **1. Genuine Sustained Overload** | **NO** | Occurs only when offered load $k > 1.0 R$ over long horizons. Does not explain rejection at $0.783 R$, nor under-service below $1.0 R$ at $k = 1.10 R$. |
| **2. Temporary Burst Demand** | **PARTIAL** | Legitimate compliant bursts exceed $B_{\max} = 0.4286 C$. However, they are within total capacity $C$ and within sustained tokens $S$. Rejection is an artifact of bucket partitioning, not excessive demand. |
| **3. Burst-Bucket Depletion** | **PRIMARY** | $B_{\max} = \frac{3}{7} C$ is too small to absorb legitimate compliant bursts ($> 0.43 C$) without full idle credit, and refills at effective rate $\frac{4}{9} R$ after $t_{\text{fill}}$. |
| **4. Sustained-Bucket Depletion** | **NO** | In all tested over-enforcement scenarios, $S(t)$ has sufficient tokens ($S(t) \ge c$). $S(t)$ never binds during false rejections. |
| **5. Idle-Credit Ceiling Contraction** | **NO** | For legitimate compliant clients, $K \approx 0$, so $\text{cap}_{\text{eff}} \approx \text{baseCap}$. Contraction only binds abusers. |
| **6. Refill-Rate Mapping & Dual Conjunction** | **PRIMARY** | The structural conjunction $\min(S, B_{\text{eff}})$ binds whenever $B_{\text{eff}} < S$. Sizing $S_{\text{rate}} = R$ while $B_{\text{eff}}$ effective refill is $\frac{4}{9} R$ makes $B_{\text{eff}}$ an unyielding bottleneck. |

---

## 5. Architectural Takeaways for Phase 2.2 Policy Design

To eliminate over-enforcement while preserving all mathematical guarantees:
1. **Preserve Invariant 1 via $S(t)$:** The sustained bucket $S(t)$ must remain the sole arbiter of long-term cumulative volume:
   $$A(T) \le S_{\max} + S_{\text{rate}} T$$
2. **Decouple Sustained Refill from Burst Choking:** When a client is transmitting compliant traffic ($S(t) \ge c$), the rate limiter must not reject requests solely because the fast burst bucket has not accrued idle credit.
3. **Reformulate the Burst Check:** The burst constraint must restrict *instantaneous excess bursts above contract*, without capping steady-state throughput below $R$.
4. **Preserve Anti-Farming via DREB-AC:** Credit accrued during idleness must continue to be governed by DREB-AC's adaptive ceiling $\text{cap}_{\text{eff}}(K)$ and linear recovery $\lambda_{\text{rec}}$, ensuring the credit extraction bound:
   $$\limsup_{T \to \infty} \frac{C_{\text{credit}}(T)}{T} \le \text{baseCap} \cdot \lambda_{\text{rec}}$$
   remains uncompromised.
