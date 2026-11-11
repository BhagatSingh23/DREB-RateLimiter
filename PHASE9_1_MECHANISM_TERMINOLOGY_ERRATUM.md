# PHASE 9.1: MECHANISM TERMINOLOGY AND STATE EQUATION ERRATUM

**Audit Date:** November 2026  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `c8df6d4`  
**Auditor:** Independent Red-Team Review  

---

## 1. Executive Summary

This audit verifies the primary algorithmic contribution claimed in the Phase 9 manuscript against the actual Java implementation ([`AdaptiveCeilingIdleCreditPolicy.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java)) and the verified Redis Lua script ([`ProductionRedisRca70Limiter.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/redis/ProductionRedisRca70Limiter.java)).

### Critical Terminology Finding
In `DREB_RCA70_MANUSCRIPT_DRAFT.md` (Sections 1, 4, 5, and Algorithm 1) and Figure 1, the dynamic suppression term:
$$C_{\text{eff}}(K) = \frac{\text{baseCap}}{1.0 + \gamma \cdot K}$$
was described interchangeably as the **"effective burst ceiling"**, the **"allowable burst ceiling"**, and the **"clamp on request cost $c$"**.

**This terminology conflates two distinct mathematical entities:**
1. **$I_{\text{ceil}}(K)$ (Adaptive Idle-Credit Ceiling):** The dynamic upper bound on the auxiliary *idle credit pool* $I$. In code, `computeEffectiveCap(state)` clamps `state.idleCredit`, NOT the primary bucket $B$, and NOT the total request cost $c$.
2. **$C_{\text{burst\_max}}(K)$ (Total Maximum Burst Admission Capacity):** The true maximum instantaneous burst that a client can discharge, which equals primary liquid tokens plus available idle credit discounted by penalty ratio $d$:
$$C_{\text{burst\_max}}(K) = B_{\max} + \frac{I_{\text{ceil}}(K)}{d} = 700.0 + \frac{450.0}{1.5(1.0 + 0.75 \cdot K)}$$

When a client has zero abuse ($K = 0$), $C_{\text{burst\_max}}(0) = 700 + 300 = \mathbf{1,000.0\,\text{tokens}}$.  
When a client is penalized at $K = 1.0$, $C_{\text{burst\_max}}(1.0) = 700 + \frac{300}{1.75} = \mathbf{871.43\,\text{tokens}}$.  
Even under infinite abuse ($K \to \infty$), the primary bucket $B_{\max} = \mathbf{700.0\,\text{tokens}}$ remains available for standard rate-limited traffic.

Describing $C_{\text{eff}}(K)$ as the "total burst ceiling" implies that client burst capacity drops to $450$ tokens or below, which contradicts both the code and the 0% False Rejection Guarantee for $c=1,000$ bursts.

---

## 2. Formal Dissection of DREB State Variables

Table 1 provides the precise mathematical definitions and code mappings for all five state components:

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: Exact State Variable Definitions, Bounds, and Functional Roles.                                |
+-----------------------+---------------------+-------------------+---------------------------------------+
| Mathematical Symbol   | Code Field Name     | Allowable Range   | Functional Operational Definition     |
+-----------------------+---------------------+-------------------+---------------------------------------+
| B(t)                  | `burstTokens` / `b` | [0, B_max = 700]  | Liquid primary tokens refilling at R. |
| S(t)                  | `sustainedTokens`/`s`| [0, S_max = 1285] | Macroscopic drain bucket for G1 bound.|
| K(t)                  | `abuseScore` / `k`  | [0, infinity)     | Dimensionless consecutive draw counter|
| I_ceil(K)             | `computeEffectiveCap`| (0, baseCap = 450]| Dynamic upper bound on idle pool I.   |
| I(t)                  | `idleCredit` / `i`  | [0, I_ceil(K)]    | Auxiliary idle credit balance.        |
| I(t) / d              | `effectiveCredit`   | [0, I_ceil(K)/d]  | Purchasing power of idle pool (d=1.5).|
| C_burst_max(K)        | B_max + I_ceil(K)/d | [700, 1000]       | True maximum single-burst capacity.   |
+-----------------------+---------------------+-------------------+---------------------------------------+
```

---

## 3. Dissecting the Actual Code Logic

In [`AdaptiveCeilingIdleCreditPolicy.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java#L52-L85):

```java
// 1. Abuse score decays continuously over quiescent dt
if (dt > 0.0 && state.abuseScore > 0.0) {
    state.abuseScore = Math.max(0.0, state.abuseScore - dt * recoveryRatePerSecond);
}

// 2. Dynamic idle ceiling is computed
double currentCap = computeEffectiveCap(state); // baseCap / (1.0 + gamma * abuseScore)

// 3. Idle credit accumulates ONLY when primary bucket is full (tFull > 0)
if (tFull > 0.0) {
    state.idleCredit = Math.min(currentCap, state.idleCredit + tFull * gain);
} else {
    // If cap shrank due to new abuse, clamp idle credit to the new cap
    state.idleCredit = Math.min(currentCap, state.idleCredit);
}

// 4. Effective burst allowance provided by the idle pool
public double effectiveCredit(ClientState state) {
    return state.idleCredit / baseDecay; // baseDecay = d = 1.5
}
```

### Key Behavioral Insights
1. **Liquid Primary Capacity is Never Reduced:** The primary bucket $B$ always refills at rate $R$ up to $B_{\max} = 700$. DREB does not punish normal, conforming baseline traffic.
2. **Ceiling Clamps Elastic Bonus Capacity:** The adaptive ceiling $I_{\text{ceil}}(K)$ specifically restricts the *bonus credit* that an idle client can hoard. A quiescent client can hoard up to $I_{\text{ceil}}(0) = 450$ credits ($300$ bonus burst tokens). A repeat-burst attacker has their bonus credit capacity reduced to $I_{\text{ceil}}(1.0) = 257.14$ credits ($171.43$ burst tokens) and eventually to near zero ($I_{\text{ceil}} \to 0$), capping them strictly at $B_{\max} = 700$.
3. **Deficit Spending Penalty:** If an incoming request cost $c$ exceeds $B$, the deficit $(c - B)$ requires $(c - B) \times d$ credits from $I$. This depletes the idle pool $1.5\times$ faster than normal tokens, while simultaneously incrementing $K$ by $(c - B) / \text{baseCap}$.

---

## 4. Reconciled Algorithm 1 Pseudocode

The pseudocode in the manuscript must be updated to match the verified Java/Lua implementation:

```
Algorithm 1: DREB-RCA70 Atomic Admission Control (Corrected)
--------------------------------------------------------------------------------
Input:  Client key u, Request cost c, Current server timestamp t
State:  B (primary), S (sustained), I (idle), K (abuse score), t_last
Output: Decision in {ADMIT, REJECT}, Updated state
--------------------------------------------------------------------------------
1:  dt <- max(0.0, t - t_last)
2:  t_last <- t
3:  // 1. Continuous Refill & Abuse Decay
4:  S <- min(S_max, S + R * dt)
5:  B_new <- B + R * dt
6:  K <- max(0.0, K - lambda_rec * dt)
7:  I_ceil <- baseCap / (1.0 + gamma * K)
8:  // 2. Accumulate Idle Credit strictly on primary overflow
9:  if B_new > B_max then
10:     t_full <- (B_new - B_max) / R
11:     B <- B_max
12:     I <- min(I_ceil, I + t_full * lambda_gain)
13: else
14:     B <- B_new
15:     I <- min(I_ceil, I)  // Clamp to contracted ceiling
16: end if
17: // 3. Multi-Pool Admission Check
18: if c > S then
19:     return REJECT
20: end if
21: if c <= B then
22:     // Covered by liquid primary tokens
23:     B <- B - c
24:     S <- S - c
25:     return ADMIT
26: else
27:     // Deficit spending drawn from idle pool
28:     deficit <- c - B
29:     required_idle <- deficit * d
30:     if required_idle <= I then
31:         I <- I - required_idle
32:         B <- 0.0
33:         S <- S - c
34:         K <- K + (deficit / baseCap)
35:         return ADMIT
36:     else
37:         return REJECT
38:     end if
39: end if
--------------------------------------------------------------------------------
```

---

## 5. Required Terminology Changes in Manuscript Draft

1. **Title & Abstract:** Replace generic phrases like "adaptive burst ceilings" with **"rest-conditioned adaptive idle-credit ceilings"**.
2. **Equation (1) & Section 4.1:** Change symbol from $C_{\text{eff}}(K)$ to $I_{\text{ceil}}(K)$ to explicitly designate that it governs the idle pool.
3. **Section 4.3:** State explicitly:
   - "Quiescent burst capacity is $C_{\text{burst\_max}}(0) = B_{\max} + I_{\text{ceil}}(0)/d = 700 + 450/1.5 = 1,000$ tokens."
   - "Post-burst penalized capacity is $C_{\text{burst\_max}}(1.0) = 700 + 257.14/1.5 = 871.43$ tokens."
4. **Figure 1 Caption:** Update caption and y-axis label from "Effective Burst Ceiling $C_{\mathrm{eff}}$" to **"Adaptive Idle-Credit Ceiling $I_{\mathrm{ceil}}(K)$"**.
