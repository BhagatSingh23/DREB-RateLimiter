# PHASE 7.3.1: G3 ABUSE RECOVERY TIME FORMAL DERIVATION

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.1 Workstream F  
**Repository Branch:** `research/systematic-improvement`  
**Starting Reference Commit:** `c4a857cd371dfb984a44ffee0684176303c84477`  

---

## 1. Executive Summary

In Phases 4, 7, and 7.3, Guarantee G3 (Abuse Recovery) cited a characteristic recovery time of **$19.0\,\text{seconds}$**. This document provides the explicit mathematical derivation of this constant from the state equations implemented in `AdaptiveCeilingIdleCreditPolicy.java` and `lua/dreb_rca70_sliding.lua`.

**Key Results:**
1. **Reference Abuse State ($K_0 = 1.0$):** Defined as the state reached when cumulative burst deficit equals nominal idle capacity ($\sum \text{deficit} = \text{baseCap} = 450.0\,\text{tokens}$).
2. **Target State ($\epsilon = 0.05$):** Defined as the state where idle ceiling capacity is restored to $\ge 96.4\%$ of nominal ($C_{\text{eff}} \ge 433.73\,\text{tokens}$).
3. **Recovery Time to $\epsilon$:**
   $$\tau_{0.05} = \frac{K_0 - \epsilon}{\lambda_{\text{rec}}} = \frac{1.0 - 0.05}{0.05} = \mathbf{19.0\,\text{seconds}}$$
4. **Complete Zero Recovery ($K = 0$):**
   $$\tau_0 = \frac{K_0 - 0}{\lambda_{\text{rec}}} = \frac{1.0}{0.05} = \mathbf{20.0\,\text{seconds}}$$
5. **Decoupling of $d$ and $\lambda_{\text{rec}}$:** Penalty debit multiplier $d = 1.5$ (governing immediate credit consumption) is orthogonal to $\lambda_{\text{rec}} = 0.05\,\text{s}^{-1}$ (governing temporal decay of the ceiling penalty).

---

## 2. State Equations for Abuse Score Accrual and Decay

The DREB-RCA70 policy tracks burst abuse via the state variable $K \ge 0$ (`state.abuseScore` in Java, `abuse_score` in Redis):

### 2.1 Abuse Score Accrual (Spend Event)
When an incoming request cost $c$ exceeds the instantaneous burst bucket balance $B$, the deficit is drawn from the idle credit pool:
$$\text{deficit} = \max(0.0, c - B)$$
The idle credit bucket is debited at penalty multiplier $d = 1.5$:
$$I \leftarrow \max(0.0, I - \text{deficit} \cdot d)$$
Concurrently, the abuse score increments in proportion to the deficit normalized by $\text{baseCap} = 450.0\,\text{tokens}$:
$$\Delta K = \frac{\text{deficit}}{\text{baseCap}}$$

**The Reference Saturated Abuse State ($K_0 = 1.0$):**  
If a client conducts repeated burst farming that accumulates $\sum \text{deficit} = \text{baseCap} = 450.0\,\text{tokens}$, the abuse score increments to:
$$K_0 = \frac{450.0}{450.0} = \mathbf{1.0}$$

### 2.2 Effective Ceiling Suppression
The effective idle credit capacity $C_{\text{eff}}(K)$ is dynamically compressed as a function of $K$:
$$C_{\text{eff}}(K) = \frac{\text{baseCap}}{1.0 + \gamma \cdot K}$$
where $\gamma = 0.75$ is the ceiling suppression factor.
- **Unpenalized State ($K = 0$):**  
  $$C_{\text{eff}}(0) = \frac{450.0}{1.0 + 0} = 450.0\,\text{tokens} \quad (100\%)$$
- **Reference Saturated State ($K_0 = 1.0$):**  
  $$C_{\text{eff}}(1.0) = \frac{450.0}{1.0 + 0.75 \times 1.0} = \frac{450.0}{1.75} \approx \mathbf{257.14\,\text{tokens}} \quad (57.14\%)$$
  At $K_0 = 1.0$, the client's idle burst capacity is suppressed by $42.86\%$.

### 2.3 Linear Temporal Recovery
Over elapsed time $\Delta t$ without burst deficit draws, the abuse score decays linearly:
$$K(t + \Delta t) = \max\left(0.0, \, K(t) - \Delta t \cdot \lambda_{\text{rec}}\right)$$
where $\lambda_{\text{rec}} = 0.05\,\text{s}^{-1}$ is the recovery rate per second.

---

## 3. Derivation of the 19.0-Second Recovery Metric

In benchmarking and adversarial evaluations (Phase 1.3, Phase 4, Phase 7), the standard recovery criterion defines an $\epsilon$-rested client as reaching $K \le \epsilon = 0.05$.

### 3.1 Why $\epsilon = 0.05$?
At $K = \epsilon = 0.05$:
$$C_{\text{eff}}(0.05) = \frac{450.0}{1.0 + 0.75 \times 0.05} = \frac{450.0}{1.0375} \approx \mathbf{433.73\,\text{tokens}}$$
$$\frac{C_{\text{eff}}(0.05)}{\text{baseCap}} = \frac{433.73}{450.0} = \mathbf{96.39\%}$$
At $\epsilon = 0.05$, the client has recovered $> 96.3\%$ of its maximum credit ceiling, rendering the remaining penalty operationally negligible.

### 3.2 Formal Derivation
Starting from the reference saturated state $K_0 = 1.0$:
$$K(\tau) = K_0 - \tau \cdot \lambda_{\text{rec}} = \epsilon$$
$$\tau \cdot \lambda_{\text{rec}} = K_0 - \epsilon$$
$$\tau = \frac{K_0 - \epsilon}{\lambda_{\text{rec}}}$$
Substituting the RCA70 parameters:
$$\tau_{0.05} = \frac{1.0 - 0.05}{0.05} = \frac{0.95}{0.05} = \mathbf{19.00\,\text{seconds}} \quad \blacksquare$$

### 3.3 Full Zero-Penalty Recovery
For complete mathematical restoration to $K = 0.0$ ($100\%$ capacity):
$$\tau_0 = \frac{K_0 - 0.0}{\lambda_{\text{rec}}} = \frac{1.0}{0.05} = \mathbf{20.00\,\text{seconds}} \quad \blacksquare$$

---

## 4. Orthogonality: Penalty Multiplier $d$ vs. Recovery Rate $\lambda_{\text{rec}}$

A point of confusion in earlier phases was whether the penalty debit multiplier $d = 1.5$ alters the 19.0-second recovery duration. They operate on distinct state dimensions:

| Parameter | Symbol | Value | State Dimension | Functional Role |
| :--- | :---: | :---: | :--- | :--- |
| **Debit Multiplier** | $d$ | $1.5$ | Idle Credit Tokens ($I$) | **Burn rate:** Consumes $1.5$ idle credits for every $1$ deficit token spent. |
| **Deficit Normalizer** | $\text{baseCap}$ | $450.0$ | Abuse Score Accrual ($\Delta K$) | Converts raw deficit tokens into dimensionless abuse score units. |
| **Suppression Factor**| $\gamma$ | $0.75$ | Ceiling Function ($C_{\text{eff}}$) | Scales the magnitude of ceiling contraction per unit of $K$. |
| **Recovery Rate** | $\lambda_{\text{rec}}$ | $0.05\,\text{s}^{-1}$ | Temporal Decay ($\frac{dK}{dt}$) | Governs the linear velocity at which $K$ decays back toward zero. |

**Conclusion:**  
$d = 1.5$ determines how quickly idle credit is exhausted during an attack, but **does not affect the time required for $K$ to decay**. The 19.0-second metric ($\tau_{0.05}$) is determined exclusively by $K_0$, $\epsilon$, and $\lambda_{\text{rec}}$.
