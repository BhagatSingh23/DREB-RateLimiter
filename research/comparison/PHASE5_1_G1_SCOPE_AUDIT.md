# Phase 5.1 — Guarantee G1 Scope Audit & Finite-Window Accounting

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`4c26bce`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Policies:** DREB-RCA70, DREB-AC BM, DREB v2 Default, Static Dual-Bucket, Token Bucket  
**Verification Test:** [`Phase51CorrectiveAuditTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase51CorrectiveAuditTest.java)

---

## 1. Executive Summary

Guarantee G1 represents the foundational safety property of the DREB rate limiting family:
$$A(T) \le E_{\text{G1}}(T) \equiv S_{\max} + R \cdot T$$
This audit independently investigates:
1. Which comparator policies are formally covered by Guarantee G1.
2. Whether the G1 cumulative bound holds **continuously at every intermediate point in time $t \in [0, T]$**, or only at simulation termination.
3. The impact of initial state conditions, floating-point precision, and finite-window burst contributions.

### Primary Audit Verdicts
- **Policy Scope:** DREB-RCA70, DREB-AC BM, DREB v2 Default, and Static Dual-Bucket are formally covered by Guarantee G1 with envelope $S_{\max} + R \cdot T$ ($S_{\max} = 1285.71$). Token Bucket is **not** covered by G1; it is governed by its own classical envelope $C + R \cdot T$ ($C = 1000.00$).
- **Continuous Intermediate Enforcement:** Automated verification in [`Phase51CorrectiveAuditTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase51CorrectiveAuditTest.java) confirms that $A(t) \le S_{\max} + R \cdot t$ holds **strictly at all 6,000 intermediate discrete request events** without exception ($Viol(t) = 0.00\,\forall t$).
- **Finite-Window Resolution:** The empirical normalized rate of $1.0212R - 1.0214R$ observed over $T = 60\,\text{s}$ is confirmed to be the exact mathematical consequence of the initial burst reservoir $S_{\max}/(R \cdot T) = +2.143\%$.

---

## 2. Policy-by-Policy Guarantee Scope Analysis

| Policy Identifier | State Variable Enforcing Rate | Formal Upper Bound Envelope | Mathematical Status | Scope Limitations |
| :--- | :--- | :--- | :--- | :--- |
| **`rca70` (DREB-RCA70)** | Sustained bucket $S(t) \in [0, S_{\max}]$ | $A(T) \le \frac{9}{7}C + R \cdot T$ | **Formally Proven** | Assumes single client key; relies on atomic debit. |
| **`dreb_ac_bm`** | Sustained bucket $S(t) \in [0, S_{\max}]$ | $A(T) \le \frac{9}{7}C + R \cdot T$ | **Formally Proven** | Identical sustained bucket structure to RCA70. |
| **`dreb_v2_default`** | Sustained bucket $S(t) \in [0, S_{\max}]$ | $A(T) \le \frac{9}{7}C + R \cdot T$ | **Formally Proven** | Identical sustained bucket structure to RCA70. |
| **`static_db_070c`** | Sustained bucket $S(t) \in [0, S_{\max}]$ | $A(T) \le \frac{9}{7}C + R \cdot T$ | **Formally Proven** | Shares identical dual-bucket refill logic. |
| **`tb_large` (Token Bucket)**| Monolithic bucket $B_{\text{TB}}(t) \in [0, C]$ | $A_{\text{TB}}(T) \le C + R \cdot T$ | **Independent Bound** | **Does not inherit G1**. Envelope is tighter by $285.71$ tokens. |

> [!WARNING]
> **Separation of Comparator Guarantees:**
> Token Bucket must not be claimed to adhere to Guarantee G1. Token Bucket operates under a different capacity envelope ($C = 1000$ vs $S_{\max} = 1285.71$).

---

## 3. Continuous Intermediate Invariant Verification

In Phase 5, envelope compliance was recorded only at the simulation boundary $T = 60.0\,\text{s}$. 

To close this verification gap, [`Phase51CorrectiveAuditTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase51CorrectiveAuditTest.java) implements a high-resolution continuous auditor:
- Workload: Sustained Overload (150% offered load, $c=15$ every $10\,\text{ms}$, 6,000 requests).
- Measurement: At every event $k \in [1, 6000]$, the auditor computes:
  $$Slack(t_k) = (S_{\max} + R \cdot t_k) - A(t_k)$$

### Mathematical Proof of Intermediate Invariant
At any instantaneous time $t \ge 0$, the sustained bucket balance is given by:
$$S(t) = S(0) + \int_0^t R\,d\tau - A(t)$$
Because the limiter enforces that no request with cost $c$ can be admitted unless $c \le S(t)$, the balance is strictly non-negative:
$$S(t) \ge 0 \quad \forall t \ge 0$$
Substituting $S(t) \ge 0$:
$$S(0) + R \cdot t - A(t) \ge 0 \implies A(t) \le S(0) + R \cdot t \le S_{\max} + R \cdot t$$
Because $S(t) \ge 0$ is a strict operational precondition for every admission, **an intermediate violation is mathematically impossible** unless an integer overflow or negative token balance occurs.

### Empirical Audit Result
Across all 6,000 steps:
- $\min_{t} Slack(t) = \mathbf{+10.71\,\text{tokens}}$ (occurring at $T=60.0\,\text{s}$).
- $\max_{t} Slack(t) = \mathbf{+1285.71\,\text{tokens}}$ (at $t=0.0\,\text{s}$).
- **Maximum Intermediate Violation:** $\mathbf{0.00\,\text{tokens}}$.

---

## 4. Initial State Conditions and Floating-Point Drift

### 4.1 Cold-Start State Invariant
- If a client initializes at full capacity ($S(0) = S_{\max}$), the cumulative envelope is $S_{\max} + R \cdot T$.
- If a client initializes from zero ($S(0) = 0$), the maximum admitted tokens over $T$ is strictly $R \cdot T$.
In all Phase 4 and Phase 5 benchmarks, limiters initialized with $S(0) = S_{\max}$, correctly exercising the maximal transient burst envelope.

### 4.2 Floating-Point Tolerance Analysis
Calculations in [`DREBLimiter.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/DREBLimiter.java) use 64-bit IEEE-754 binary floating-point (`double`, 53 bits of significand):
$$\epsilon_{\text{machine}} \approx 1.11 \times 10^{-16}$$
Over 6,000 iterative refill steps ($\Delta t = 0.010\,\text{s}$, $R = 1000.0$), cumulative arithmetic rounding error is bounded by:
$$|\text{Error}_{\text{accumulated}}| \le 6000 \times \epsilon_{\text{machine}} \times R \approx 6.66 \times 10^{-10}\,\text{tokens}$$
This drift is more than ten orders of magnitude below the minimum token cost ($c=1$), proving that floating-point inaccuracy has zero impact on decision outcomes.

---

## 5. Reconciliation of Historical Phase Throughput Claims

| Project Phase | Workload Evaluated | Observed Rate ($r_{\text{avg}}$) | Observation Window ($T$) | Permitted Bound ($1 + S_{\max}/(R \cdot T)$) | Compliance Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Phase 2 Sweep** | Overload Sweep ($1.50R$) | $1.0212 R$ | $60.0\,\text{s}$ | $1.02143 R$ | **Fully Compliant** ($Slack = +10.7\,\text{tokens}$) |
| **Phase 4 Adversarial**| Attack E3 (Micro-Cost Flood)| $1.0214 R$ | $60.0\,\text{s}$ | $1.02143 R$ | **Fully Compliant** ($Slack = +1.7\,\text{tokens}$) |
| **Phase 5 Comparison** | Workload W03 (Overload 1.5R) | $1.0212 R$ | $60.0\,\text{s}$ | $1.02143 R$ | **Fully Compliant** ($Slack = +10.7\,\text{tokens}$) |

**Conclusion:** All historical throughput observations near $1.02R$ are fully reconciled and certified mathematically compliant with Guarantee G1.
