# PHASE 8: MATHEMATICAL CLAIM EVIDENCE MATRIX AND PROOF AUDIT

**Document:** `research/synthesis/PHASE8_MATHEMATICAL_CLAIM_EVIDENCE_MATRIX.md`  
**Target Policy:** DREB-RCA70 (`com.dreb.ratelimit.policies.AdaptiveCeilingIdleCreditPolicy`)  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `4ff9d0a`  

---

## 1. Executive Summary

This matrix audits every mathematical theorem, rate envelope, excess formula, and boundary condition established across Phases 1 through 7.3.4. 

We explicitly separate:
1. **Mathematical Theorems:** Analytically derived proofs from formal state equations.
2. **Empirical Observations:** Measured behaviors in simulated and containerized testbeds.
3. **Required Operational Assumptions:** Prerequisite conditions necessary for theorems to hold.
4. **Deployment Enforcement Status:** Whether the condition is actively enforced by infrastructure or merely assumed.

---

## 2. Definitive Mathematical Claim Evidence Matrix

| Mathematical Claim | Formal Expression | Operational Scope | Underlying Assumptions | Evidential Basis | Testbed Status | Limitations & Disclosures |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **G1 Local Rate Bound** | $A(T) \le S_{\max} + R \cdot T$ ($1285.7143 + 1000 \cdot T$) | Single healthy Redis primary node | Single-threaded Lua script atomicity; local `redis.call('TIME')` | **`[MATHEMATICALLY_PROVED] & [EXPERIMENTALLY_OBSERVED]`** | Verified in `Phase7ProductionAdapterTest` | Valid only within local server time; violated across multi-host failover. |
| **Timing-Tolerant $G_{1,\delta}$ Envelope** | $A(T) \le S_{\max} + R(T + \delta) = 1295.7143 + 1000 \cdot T$ | Multi-host distributed physical time | Absolute UTC offset $\|\tau - t\| \le 5\,\text{ms}$; slew-only Chrony (`makestep 0 0`) | **`[CONDITIONAL_MATHEMATICAL_THEOREM]`** | **`[ASSUMED_NOT_ENFORCED]`** | Shared VM kernel had zero drift by colocation; NOT experimentally validated on multi-host NTP. |
| **Two-Point Timing Error Bound** | $\|\Delta \tau - T\| \le 2\epsilon = 10.0\,\text{ms}$ ($\delta = 0.010\,\text{s}$) | Cross-node readings at $t_1, t_2$ | Each reading bounded by $\|\tau_i - t_i\| \le \epsilon = 5.0\,\text{ms}$ | **`[MATHEMATICALLY_PROVED]`** | Proven via triangle inequality | Pure analytical deduction; relies on host NTP disciplining. |
| **Single-Event Burst Cap** | $A_{\text{event\_burst}} \le 2 \times C_{\max} = 2000.0\,\text{tokens}$ | Single client key across 1 isolated reset | Exactly 1 isolated event $E$ (failover, recreation, or clock step) | **`[MATHEMATICALLY_PROVED] & [EXPERIMENTALLY_OBSERVED]`** | Verified in `Phase73ReconciliationTest` | **Attainable maximum** per client key; does NOT bound multi-client aggregate cluster load. |
| **Single-Event Cumulative Total** | $A_{\text{total}}(T) \le 2000.0 + 1000.0 \cdot T\,\text{tokens}$ | Single client key over physical interval $T$ | Monotonic refill progression outside the two burst draws | **`[MATHEMATICALLY_PROVED]`** | Analytical partition | At $T=10\,\text{ms}$, cumulative limit is $2010.0\,\text{tokens}$. |
| **Single-Event Excess vs $G_1$ (10 ms)** | $\Delta_{G_1}(0.010) = 2000.0 - 1295.7143 = \mathbf{+704.2857\,\text{tokens}}$ | Single client key at $T = 10\,\text{ms}$ | Compared against ideal physical envelope $G_1(0.010)$ | **`[MATHEMATICALLY_PROVED] & [EXPERIMENTALLY_OBSERVED]`** | Verified in `Phase73ReconciliationTest` | Transient surge (+54.35%) during un-replicated failover. |
| **Single-Event Excess vs $G_{1,\delta}$ (10 ms)** | $\Delta_{G_{1,\delta}}(0.010) = 2000.0 - 1305.7143 = \mathbf{+694.2857\,\text{tokens}}$ | Single client key at $T = 10\,\text{ms}$ | Compared against relaxed envelope $G_{1,\delta}(0.010)$ | **`[MATHEMATICALLY_PROVED]`** | Analytical deduction | Incorporating timing allowance reduces nominal excess by $10.0\,\text{tokens}$. |
| **Single-Event Excess vs $G_1$ ($T \to 0$)** | $\lim_{T \to 0} \Delta_{G_1}(T) = 2000.0 - 1285.7143 = \mathbf{+714.2857\,\text{tokens}}$ | Theoretical instantaneous limit | Single reset; instantaneous requests $c_1=c_2=1000$ | **`[MATHEMATICALLY_PROVED]`** | Analytical limit | Worst-case theoretical over-admission. |
| **Single-Event Excess vs $G_{1,\delta}$ ($T \to 0$)** | $\lim_{T \to 0} \Delta_{G_{1,\delta}}(T) = 2000.0 - 1295.7143 = \mathbf{+704.2857\,\text{tokens}}$ | Theoretical instantaneous limit | Compared against relaxed envelope at $T = 0$ | **`[MATHEMATICALLY_PROVED]`** | Analytical limit | Coincides with $\Delta_{G_1}(0.010)$ due to identical $10\,\text{token}$ shift. |
| **Repeated Discontinuities Divergence** | $A(N_E) \le (N_E(T) + 1) \cdot C_{\max} \to \infty$ | $N_E(T)$ unconstrained resets | Unbounded failovers or forward clock jumps | **`[MATHEMATICALLY_PROVED] & [EXPERIMENTALLY_OBSERVED]`** | Verified in `Phase73ReconciliationTest` (3 jumps $\rightarrow$ 4,000 tokens) | **No finite global bound exists** without an operational limit on jump frequency. |
| **G2 Asymptotic Convergence (Sufficiency)** | $\lim_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le 1000.0\,\text{tokens/s}$ | All client workloads | Sublinear event frequency: $\lim \frac{N_E(T)}{T} = 0$; bounded initial state | **`[MATHEMATICALLY_PROVED]`** | Analytical proof via limit calculus | **Universally sufficient** for all workloads. Does NOT repair finite-window G1 burst spikes. |
| **G2 Asymptotic Convergence (Necessity)** | $\lim \frac{N_E(T)}{T} = 0 \iff \lim \frac{A(T)}{T} \le R$ | Saturated adversarial demand only | Persistent exploitation: $c_k \ge c_{\min} > 0$ across all resets; full $R$ drawn | **`[MATHEMATICALLY_PROVED]`** | Proof by contradiction | Fails for quiescent workloads (sublinear frequency is NOT necessary for idle clients). |
| **G3 Abuse Recovery Time ($\tau_{0.05}$)** | $\tau_{0.05}(K_0) = \frac{K_0 - 0.05}{0.05} = 20(K_0 - 0.05)\,\text{s}$ | Recovery to $\ge 96.4\%$ credit ceiling | Client ceases deficit spending; linear decay $\lambda_{\text{rec}} = 0.05\,\text{s}^{-1}$ | **`[MATHEMATICALLY_PROVED] & [EXPERIMENTALLY_OBSERVED]`** | Verified across all phases | For $K_0 = 1.0$, $\tau_{0.05} = \mathbf{19.0\,\text{s}}$. 19.0s is NOT a universal constant. |
| **G3 Full Zero Recovery Time ($\tau_0$)** | $\tau_0(K_0) = \frac{K_0}{0.05} = 20 K_0\,\text{s}$ | Recovery to $100\%$ capacity ($K = 0$) | Quiescent traffic until $K = 0.0$ | **`[MATHEMATICALLY_PROVED]`** | Analytical derivation | For $K_0 = 1.0$, $\tau_0 = \mathbf{20.0\,\text{s}}$. Token balance refill takes additional time. |
| **Parameter Orthogonality** | $\frac{\partial (dK/dt)}{\partial d} = 0$ | State dynamics of $I$ vs $K$ | Debit multiplier $d=1.5$ operates on $I$; $\lambda_{\text{rec}}=0.05$ operates on $K$ | **`[MATHEMATICALLY_PROVED]`** | Code inspection and derivation | $d$ controls token burn velocity; $\lambda_{\text{rec}}$ controls temporal forgiveness velocity. |
| **Gate H Idempotency Contract** | At-most-once token deduction | Single-node and failover transactions | 5 conditions: same ID, same hash, $\Delta t < 60\,\text{s}$, `WAIT 1`, record visible on replica | **`[CONDITIONAL]`** | `WAIT 1` verified; async failover verified to drop | In standard async Redis failover, un-replicated records are lost, causing **duplicate debits**. |

---

## 3. Explicit Audit Disclosures for Academic Submission

To avoid reviewer rejection based on over-claiming, the following distinctions must be strictly observed in the text:

1. **Testbed vs. Production Clock Reality:**  
   The statement *"$G_{1,\delta}$ bounds physical-time admissions to $1295.7143 + 1000 T$"* must always be preceded by the explicit qualification: *"Under the assumption that host operating systems maintain continuous slew-only clock synchronization within $\pm 5\,\text{ms}$..."*. The paper must explicitly disclose that the experimental container testbed ran on a single host VM and therefore did not demonstrate multi-server physical NTP disciplining.

2. **Replication Ack vs. Consensus:**  
   The mechanism `WAIT 1 1000` must never be referred to as "synchronous replication" or "consensus" without qualifying that it is an **in-memory volatile acknowledgement**. Disk durability remains asynchronous.

3. **Per-Key vs. Cluster-Wide Bounds:**  
   The single-event bound $A_{\text{event\_burst}} \le 2000.0\,\text{tokens}$ must never be described as a cluster-wide ceiling. It is strictly a **per-client-key bound** under an isolated single-node transition.

4. **G2 Sufficiency vs. Necessity:**  
   The condition $\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$ must be identified as **sufficient for all workloads**, but **necessary only under a saturated adversarial demand model**.
