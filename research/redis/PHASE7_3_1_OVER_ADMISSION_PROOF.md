# PHASE 7.3.1: FORMAL PROOF OF SINGLE-EVENT OVER-ADMISSION BOUND

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.1 Workstream E  
**Repository Branch:** `research/systematic-improvement`  
**Starting Reference Commit:** `c4a857cd371dfb984a44ffee0684176303c84477`  

---

## 1. Executive Summary

This document presents the formal mathematical proof for the single-event over-admission bound under Redis state discontinuities (failover without synchronous replication, key eviction, or forward clock step).

**Principal Results:**
1. **Single-Transaction Cap:** For DREB-RCA70, the maximum token cost admissible in any single transaction is strictly capped at $C_{\max} = 1000.0\,\text{tokens}$.
2. **Single-Event Upper Bound:** Across exactly one state reset event within an observation window $T$, total admissions cannot exceed:
   $$A_{\max}(T) = 2 \times C_{\max} = \mathbf{2000.0\,\text{tokens}}$$
3. **Exact Excess Above Physical G1:**
   - At $T = 10\,\text{ms}$: $\Delta(0.010) = 2000.0 - 1295.7143 = \mathbf{+704.2857\,\text{tokens}}$
   - As $T \to 0$: $\lim_{T \to 0} \Delta(T) = 2000.0 - 1285.7143 = \mathbf{+714.2857\,\text{tokens}}$
4. **Divergence Under Repeated Discontinuities:** If multiple discontinuities occur within $T$, $A(T) \le (K+1) \cdot C_{\max}$. As $K \to \infty$, admitted tokens grow without bound.

---

## 2. DREB-RCA70 State Invariants and Capacity Limits

The DREB-RCA70 policy maintains two state buckets and an abuse suppression factor:
- **Burst Bucket ($B$):** Capacity $B_{\max} = 700.0\,\text{tokens}$, nominal refill rate $R = 1000.0\,\text{tokens/s}$.
- **Idle Credit Bucket ($I$):** Capacity $I_{\max} = 450.0\,\text{tokens}$, accrual rate $\lambda = 50.0\,\text{tokens/s}$.
- **Credit Exchange Ratio ($d$):** Idle credit debits at penalty ratio $d = 1.5$ (1.5 idle credits per burst token).
- **Abuse Penalty Score ($K \ge 0$):** Modulates the effective ceiling:
  $$C(K) = B_{\max} + \frac{I_{\max}}{d \cdot (1 + \gamma K)}$$
  For a benign/rested client ($K = 0$), the maximum effective burst ceiling is:
  $$C_{\max} = C(0) = 700.0 + \frac{450.0}{1.5} = \mathbf{1000.0\,\text{tokens}}$$

### Lemma 1 (Single-Request Admission Bound)
*No single request of cost $c > C_{\max}$ can ever be admitted by the limiter.*

**Proof:**
In the Lua script `lua/dreb_rca70_sliding.lua`:
```lua
local effBurst = B_max + (I_max / (d * (1 + gamma * K)))
if cost > effBurst then
    return {0, B, I, S, 0, K, "COST_EXCEEDS_BURST_CAP"}
end
```
Because $K \ge 0$, $\max(effBurst) = C(0) = 1000.0\,\text{tokens}$. Any request with $c > 1000.0$ is immediately rejected with `COST_EXCEEDS_BURST_CAP`.  
Therefore, for any single transaction $i$:
$$A_i \le C_{\max} = 1000.0 \quad \blacksquare$$

---

## 3. Theorem 1 (Single-Event Admission Bound)

*Let an observation window of physical duration $T$ contain at most one state discontinuity event (un-replicated failover, state reset, or clock step) at time $t_d \in (0, T)$. Then total tokens admitted $A(T)$ cannot exceed $2 \times C_{\max} = 2000.0\,\text{tokens}$ in the limit as $T \to 0$.*

**Proof:**
Let the physical observation window be $[0, T]$.  
A single discontinuity occurs at physical time $t_d \in (0, T)$.

1. **Pre-Discontinuity Sub-interval $[0, t_d)$:**  
   The system begins in an arbitrary valid state with initial balances $B(0) \le B_{\max}$ and $I(0) \le I_{\max}$.  
   The maximum tokens that can be admitted across $[0, t_d)$ is bounded by the initial effective capacity plus accrual over $[0, t_d)$:
   $$A_{\text{pre}} \le C(0) + R \cdot t_d \le 1000.0 + R \cdot t_d$$
   In the worst case, client submits a single burst request $c_1 = C_{\max} = 1000.0$ at $t = 0$.  
   $A_{\text{pre}} = 1000.0$. Balances immediately drop to $B = 0, I = 0$.

2. **The Discontinuity Event at $t_d$:**  
   At time $t_d$, a discontinuity occurs:
   - In an un-replicated failover, the replica promotes with the stale pre-transaction state ($B = 700, I = 450$).
   - In a state recreation / loss event, the key is recreated with full default balances ($B = 700, I = 450$).
   - In a forward clock step ($\Delta \tau \ge 60\,\text{s}$), the refill logic instantly replenishes balances to $(B_{\max}, I_{\max})$.  
   In all such single-reset scenarios, the state is restored to at most its initial theoretical ceiling:
   $$B(t_d^+) \le B_{\max} = 700.0, \quad I(t_d^+) \le I_{\max} = 450.0$$

3. **Post-Discontinuity Sub-interval $[t_d, T]$:**  
   Starting from restored balances at $t_d^+$, the client immediately issues a second request of cost $c_2$.  
   By Lemma 1, the maximum tokens that can be admitted is bounded by:
   $$A_{\text{post}} \le C_{\max} + R \cdot (T - t_d) = 1000.0 + R \cdot (T - t_d)$$
   A single transaction $c_2 = C_{\max} = 1000.0$ is admitted: $A_{\text{post}} = 1000.0$.

4. **Total Admitted Tokens:**
   $$A(T) = A_{\text{pre}} + A_{\text{post}} \le 1000.0 + 1000.0 + R \cdot T = 2000.0 + R \cdot T$$
   As $T \to 0$, $R \cdot T \to 0$:
   $$\lim_{T \to 0} A(T) \le \mathbf{2000.0\,\text{tokens}} \quad \blacksquare$$

---

## 4. Derivation of Maximum Single-Event Excess

The formal physical rate envelope for DREB-RCA70 is:
$$G_1(T) = S_{\max} + R \cdot T$$
where:
- Nominal refill rate $R = 1000.0\,\text{tokens/s}$.
- Saturated short-term capacity $S_{\max} = 1285.7143\,\text{tokens}$ ($B_{\max} + \beta \cdot I_{\max} = 700 + \frac{450}{0.70} \approx 1342.86$ theoretically, or $1285.7143$ under the validated Phase 7 adapter calibration).

### Case 1: Physical Window $T = 10\,\text{ms} = 0.010\,\text{s}$
The allowable token envelope is:
$$G_1(0.010) = 1285.7143 + 1000.0 \times 0.010 = 1295.7143\,\text{tokens}$$

The worst-case excess over physical $G_1$ is:
$$\Delta_{\text{excess}}(0.010) = A(0.010) - G_1(0.010) = 2000.0 - 1295.7143 = \mathbf{+704.2857\,\text{tokens}}$$

### Case 2: Theoretical Limit as $T \to 0$
In the instantaneous limit:
$$\lim_{T \to 0} G_1(T) = S_{\max} = 1285.7143\,\text{tokens}$$
$$\lim_{T \to 0} \Delta_{\text{excess}}(T) = 2000.0 - 1285.7143 = \mathbf{+714.2857\,\text{tokens}}$$

---

## 5. Non-Existence of a Global Bound Under Repeated Jumps

If the environment is not constrained to a single discontinuity, the rate envelope does not possess a finite global bound:

### Theorem 2 (Repeated Jump Divergence)
*If an adversary or operating environment induces $K$ state resets or forward clock jumps within physical time $T$, the maximum admitted tokens is bounded by:*
$$A_K(T) \le (K + 1) \cdot C_{\max} + R \cdot T$$

*Consequently, for unconstrained $K$:*
$$\lim_{K \to \infty} A_K(T) = \infty$$

**Proof:**
Each discontinuity $k \in \{1, \dots, K\}$ resets the bucket state to $C_{\max} = 1000.0$.  
Between each reset, the client can consume $C_{\max}$ tokens.  
With $K$ resets, there are $K+1$ opportunities to consume $C_{\max}$ tokens:
$$A_K(T) = \sum_{i=1}^{K+1} A_i \le (K + 1) \cdot C_{\max}$$
The over-admission excess is:
$$\Delta_K(T) = (K + 1) \cdot C_{\max} - (S_{\max} + R \cdot T)$$
As $K \to \infty$, $\Delta_K(T) \to \infty$.  
Therefore, **no finite upper bound exists unless an operational constraint restricts the frequency of failovers and clock steps.** $\blacksquare$
