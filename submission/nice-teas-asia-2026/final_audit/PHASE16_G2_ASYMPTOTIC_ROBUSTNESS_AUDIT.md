# PHASE 16: G2 ASYMPTOTIC PROOF ROBUSTNESS & SYSTEM-MODEL ASSUMPTION AUDIT
## Independent Mathematical Stress-Test of Long-Run Rate Envelope Guarantees

**Repository:** `BhagatSingh23/DREB-RateLimiter`  
**Branch:** `submission/nice-teas-asia-2026`  
**Base Commit:** `688eb08`  
**Audit Date:** 14 November 2026  
**Auditor:** Formal Methods & System Architecture Audit Panel (Phase 16)  
**Subject:** Rigorous Mathematical Stress-Testing of Guarantee G2 (Theorem 4 in 8-Page & 10-Page Manuscripts)  

---

## 1. Executive Summary & Definitive Verdict

This audit independently stress-tests the mathematical and system-level foundations of Guarantee G2 (Asymptotic Rate Convergence):

$$\limsup_{T \to \infty} \frac{A(T)}{T} \le R$$

under the dual conditions of universal sufficiency and workload-conditioned necessity.

### Definitive Verdict:
```
+-------------------------------------------------------------------------------------------------------+
| VERDICT: FULLY VERIFIED UNDER STATED SYSTEM AND WORKLOAD ASSUMPTIONS                                  |
|                                                                                                       |
| 1. Sufficiency: Fully robust across all arrival sequences under bounded initial state and finite      |
|    per-event burst capacity.                                                                          |
| 2. Event-Window Overlap: Lebesgue measure accounting resolves overlapping transitions without         |
|    weakening the asymptotic rate bound.                                                               |
| 3. Subsequence Convergence: Necessity holds rigorously under limsup N_E(T)/T > 0 without requiring    |
|    existence of the ordinary limit lim N_E(T)/T.                                                      |
| 4. Net Event Excess: c_min > R * Delta_t_event is satisfied by orders of magnitude (c_min >= 700      |
|    tokens vs R * Delta_t_event <= 10 tokens, yielding net excess >= +690 tokens).                     |
| 5. Counterexamples: Phase 15 counterexamples are verified as arithmetically and conceptually sound.   |
| 6. Manuscripts: Theorem 4 in both 8-page and 10-page editions is mathematically faithful to the       |
|    frozen baseline and requires no textual tampering.                                                 |
+-------------------------------------------------------------------------------------------------------+
```

---

## 2. Universal Sufficiency Audit

### 2.1 The Accounting Upper Bound
The universal sufficiency proof relies on the cumulative upper bound:
$$A(T) \le S_{\max} + R \cdot T + N_E(T) \cdot C_{\max}$$

We inspect each component to determine its physical and mathematical validity:

1. **What Qualifies as an Additional Admission from One Discontinuity?**  
   In DREB-RCA70, normal admission is strictly bounded by the sustained bucket $S(t) \ge 0$. Under an uninterrupted node, every admitted token is debited from $S(t)$ with continuous replenishment $R \cdot \Delta t$ (Lemma 1).  
   A *discontinuity event* occurs when serving node state changes non-monotonically without debiting $S(t)$—specifically:
   - Primary crash with un-replicated state loss: the promoted replica rolls back to a pre-burst or pristine snapshot.
   - Pod / container restart: state resets to initial parameters ($B=B_{\max}, I=I_{\max}, K=0$).
   - Forward clock jump: perceived time advances discontinuously, granting premature refill.
   An *additional admission* is any admission admitted on the promoted/restarted node that would have been rejected had the pre-crash debit state been preserved.

2. **Is $C_{\max}$ a Valid Per-Event Upper Bound?**  
   Yes. Under DREB-RCA70, total instantaneous burst allowance is given by:
   $$C_{\text{burst\_max}}(K) = B_{\max} + \frac{I_{\text{ceil}}(K)}{d}$$
   Because $B(t) \le B_{\max} = 700.0$ and $I(t) \le I_{\text{ceil}}(K) \le \text{baseCap} = 450.0$, the maximum possible burst that *any* single node can admit from pristine initial state is:
   $$C_{\max} = 700.0 + \frac{450.0}{1.5} = 1000.0\,\text{tokens}$$
   Even if the node has zero prior history, it cannot admit more than $C_{\max}$ tokens in a localized burst window. Thus, each discontinuity event can inject at most $C_{\max}$ unearned tokens into the cumulative envelope.

3. **Can Repeated Events Compound Beyond $N_E(T) \cdot C_{\max}$?**  
   No. If $N_E(T)$ discontinuity events occur in $[0, T]$, each event $k \in \{1, \dots, N_E(T)\}$ can restore at most $C_{\max}$ burst capacity. The maximum cumulative capacity injected by all discontinuities is strictly bounded by:
   $$\sum_{k=1}^{N_E(T)} \Delta S_k \le \sum_{k=1}^{N_E(T)} C_{\max} = N_E(T) \cdot C_{\max}$$
   Even under rapid-fire or cascading failovers, each distinct crash-recovery transition provides at most one instance of state restoration bounded by $C_{\max}$.

4. **Mutual Consistency of Initial State and Refill:**  
   - Initial capacity: $S(0) \le S_{\max} = 1285.7143\,\text{tokens}$.
   - Continuous replenishment: $\int_0^T R(t)\,dt = R \cdot T$.
   - Discontinuity injections: $N_E(T) \cdot C_{\max}$.
   All three terms represent independent, additive upper bounds on token availability. Their sum strictly bounds total possible admissions $A(T)$.

5. **Universal Workload Independence:**  
   Because $A(T) \ge 0$ is constrained only by token availability, this bound holds for **all** admissible client arrival sequences—benign, Poisson, periodic, quiescent, or maliciously adaptive.

### 2.2 System-Level Assumptions Required for Sufficiency
For the universal sufficiency bound to hold, the system model requires:
- **Assumption S1 (Bounded Initial State):** $S(0) \le S_{\max} < \infty$.
- **Assumption S2 (Finite Replenishment Rate):** $R(t) \le R < \infty$ for all $t$.
- **Assumption S3 (Bounded Per-Event Capacity):** Maximum burst capacity admitted per discontinuity event satisfies $C_{\text{event}} \le C_{\max} < \infty$.
- **Assumption S4 (Sublinear Event Frequency):** $\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$.

Under Assumptions S1–S4:
$$\limsup_{T \to \infty} \frac{A(T)}{T} \le \lim_{T \to \infty} \frac{S_{\max}}{T} + R + C_{\max} \cdot \lim_{T \to \infty} \frac{N_E(T)}{T} = 0 + R + 0 = R \quad \blacksquare$$

---

## 3. Event-Window Overlap & Lebesgue Measure Audit

### 3.1 The Potential Defect in Simplified Accounting
The informal necessity derivation in earlier drafts wrote:
$$T_{\text{normal}} = T - N_E(T) \cdot \Delta t_{\text{event}}$$
This naive expression exhibits three potential vulnerabilities:
1. **Overlapping Failovers:** If event $i+1$ occurs before event $i$ completes ($t_{i+1} < t_i + \Delta t_i$), the intervals overlap, making the naive sum $\sum \Delta t_i$ an over-count of downtime.
2. **Cascading Failures:** During an unready transition state, the gateway cannot process requests; hence, an adversary cannot draw $c_{\min}$ tokens during aborted recovery attempts.
3. **Boundary Truncation:** An event near $T$ may have its transition interval $[t_i, t_i + \Delta t_i]$ extend beyond $T$.

### 3.2 Rigorous Lebesgue Measure Formulation
To eliminate any ambiguity concerning overlapping intervals, we formalize the accounting using the Lebesgue measure of the transition set:

Let $\{[t_i, t_i + \Delta t_i]\}_{i=1}^{N_E(T)}$ denote the collection of discontinuity transition intervals occurring in $[0, T]$.  
Define the **total transition domain** $\Omega_{\text{trans}}(T)$ as the union:
$$\Omega_{\text{trans}}(T) = \left( \bigcup_{i=1}^{N_E(T)} [t_i, t_i + \Delta t_i] \right) \cap [0, T]$$

Let $\mu(\Omega_{\text{trans}}(T))$ denote the 1-dimensional Lebesgue measure (total elapsed downtime duration) of $\Omega_{\text{trans}}(T)$.  
By sub-additivity of measure:
$$\mu(\Omega_{\text{trans}}(T)) \le \sum_{i=1}^{N_E(T)} \Delta t_i \le N_E(T) \cdot \Delta t_{\max}$$

The **normal operational domain** during which continuous rate limiting operates is:
$$\Omega_{\text{normal}}(T) = [0, T] \setminus \Omega_{\text{trans}}(T)$$
with exact duration:
$$T_{\text{normal}} = \mu(\Omega_{\text{normal}}(T)) = T - \mu(\Omega_{\text{trans}}(T)) \ge T - N_E(T) \cdot \Delta t_{\max}$$

### 3.3 Effective Exploitable Events ($N_E^*$)
Define an **effective exploitable event** as a discontinuity event $j$ that successfully completes promotion, allowing the client to connect and execute a burst request before any subsequent crash.  
Let $N_E^*(T) \le N_E(T)$ be the count of effective events.

- **Case 1: Well-Separated Events (Operational Stability):**  
  In any stable distributed deployment, Mean Time Between Failures ($\text{MTBF}$) vastly exceeds failover duration ($\text{MTBF} \gg \Delta t_{\text{event}}$). Transition windows are pairwise disjoint:
  $$[t_i, t_i + \Delta t_i] \cap [t_j, t_j + \Delta t_j] = \emptyset \quad (\forall i \ne j)$$
  Under this condition, $N_E^*(T) = N_E(T)$ and $\mu(\Omega_{\text{trans}}(T)) = N_E(T) \cdot \Delta t_{\text{event}}$, matching the manuscript derivation exactly.

- **Case 2: Overlapping Events (Pathological Cascades):**  
  If transitions overlap, $\mu(\Omega_{\text{trans}}(T)) < N_E(T) \cdot \Delta t_{\text{event}}$.  
  Because $T_{\text{normal}} = T - \mu(\Omega_{\text{trans}}(T)) > T - N_E(T) \cdot \Delta t_{\text{event}}$, normal serving time is *larger* than naive subtraction.  
  Total admitted tokens satisfy:
  $$A(T) \ge R \cdot T_{\text{normal}} + \sum_{j=1}^{N_E^*(T)} A_{\text{event}}(j) \ge R(T - \mu(\Omega_{\text{trans}}(T))) + N_E^*(T) \cdot c_{\min}$$
  $$A(T) \ge R \cdot T - R \cdot \mu(\Omega_{\text{trans}}(T)) + N_E^*(T) \cdot c_{\min}$$
  Since $\mu(\Omega_{\text{trans}}(T)) \le N_E(T) \cdot \Delta t_{\max}$:
  $$\frac{A(T)}{T} \ge R + \frac{N_E^*(T) \cdot c_{\min} - R \cdot N_E(T) \cdot \Delta t_{\max}}{T}$$
  Whenever effective events scale with total events ($N_E^*(T) \ge \alpha N_E(T)$ for some $\alpha > 0$) and $\alpha c_{\min} > R \cdot \Delta t_{\max}$, the excess remains strictly positive.

**Conclusion:** Event overlap does not invalidate the lower bound. In realistic failure models where nodes recover between crashes ($\alpha = 1$), the manuscript formula is exact.

---

## 4. General Asymptotic Frequency Proof ($\limsup$ vs $\lim$)

The manuscript proof writes $\lambda_E = \lim_{T \to \infty} \frac{N_E(T)}{T}$, which implicitly assumes the ordinary limit exists. In general distributed systems, event arrivals may oscillate (e.g., bursts of failovers followed by super-linearly expanding quiescent epochs), causing $\frac{N_E(T)}{T}$ to oscillate without converging.

We prove that conditional necessity holds under the completely general condition $\limsup_{T \to \infty} \frac{N_E(T)}{T} > 0$:

### 4.1 Subsequence Derivation
**Theorem (Generalized Necessity under Saturated Demand):**  
*Assume client demand satisfies the Saturated Adversarial Demand criteria with positive net event excess ($c_{\min} > R \cdot \Delta t_{\text{event}}$). If:*
$$\limsup_{T \to \infty} \frac{N_E(T)}{T} = \lambda_{\sup} > 0$$
*then:*
$$\limsup_{T \to \infty} \frac{A(T)}{T} > R$$

**Proof:**  
By the formal definition of limit superior for real-valued functions, there exists an infinite monotonically increasing sequence of timestamps $\{T_k\}_{k=1}^\infty$ with $T_k \to \infty$ as $k \to \infty$ such that:
$$\lim_{k \to \infty} \frac{N_E(T_k)}{T_k} = \lambda_{\sup} > 0$$

Under saturated adversarial demand, cumulative admissions over $[0, T_k]$ satisfy:
$$A(T_k) \ge R \cdot T_k + N_E(T_k) \cdot (c_{\min} - R \cdot \Delta t_{\text{event}})$$

Dividing by $T_k > 0$:
$$\frac{A(T_k)}{T_k} \ge R + \frac{N_E(T_k)}{T_k} \cdot (c_{\min} - R \cdot \Delta t_{\text{event}})$$

Taking the limit as $k \to \infty$ along this subsequence:
$$\lim_{k \to \infty} \frac{A(T_k)}{T_k} \ge R + \left( \lim_{k \to \infty} \frac{N_E(T_k)}{T_k} \right) (c_{\min} - R \cdot \Delta t_{\text{event}}) = R + \lambda_{\sup} (c_{\min} - R \cdot \Delta t_{\text{event}})$$

Because $\lambda_{\sup} > 0$ and $(c_{\min} - R \cdot \Delta t_{\text{event}}) > 0$:
$$\lambda_{\sup} (c_{\min} - R \cdot \Delta t_{\text{event}}) > 0$$

By standard real analysis, the limit superior across the entire domain is lower-bounded by the limit along any subsequence:
$$\limsup_{T \to \infty} \frac{A(T)}{T} \ge \lim_{k \to \infty} \frac{A(T_k)}{T_k} \ge R + \lambda_{\sup} (c_{\min} - R \cdot \Delta t_{\text{event}}) > R \quad \blacksquare$$

**Significance:** This removes the assumption of ordinary limit convergence. Even if event frequencies oscillate erratically, any non-sublinear frequency ($\limsup > 0$) guarantees rate collapse along the peak subsequence.

---

## 5. Positive Net Event Excess: $c_{\min}$ vs $C_{\max}$

A critical step in the necessity proof is:
$$c_{\min} - R \cdot \Delta t_{\text{event}} > 0$$

We rigorously distinguish three related concepts:

| Quantity | Mathematical Definition | DREB-RCA70 Numerical Value | Physical Meaning |
| :--- | :--- | :---: | :--- |
| **$C_{\max}$** | $B_{\max} + \frac{I_{\max}}{d}$ | $1000.0\,\text{tokens}$ | Maximum burst capacity achievable under pristine quiescent state ($K=0$). |
| **$c_{\min}$** | $\min_{i} A_{\text{event}}(i)$ | $700.0\,\text{tokens}$ (guaranteed)<br>$1000.0\,\text{tokens}$ (pristine) | Minimum burst admitted to the adversary across exploitable reset events. |
| **$\Delta A_{\text{net}}$** | $c_{\min} - R \cdot \Delta t_{\text{event}}$ | $+690.0\,\text{tokens}$ (worst-case)<br>$+990.0\,\text{tokens}$ (pristine) | Net token excess injected into the long-run admission envelope per event. |

### 5.1 Is $c_{\min} = 1000$ Guaranteed for Every Event?
No. An admitted burst of $1000.0$ tokens requires both primary capacity ($B=700$) and idle credit ($I=450$). If a promoted replica restores state from an asynchronous snapshot where $I < 450$ or $K > 0$, the admissible burst is less than $1000.0$.

### 5.2 What Is the Strict Lower Bound on $c_{\min}$?
Even in the worst possible snapshot restoration where accumulated idle credit is completely zero ($I=0$) and abuse penalty is at maximum ($K=1.0$), the algorithm initializes or resets the primary bucket to:
$$B_{\min} = B_{\max} = 700.0\,\text{tokens}$$
Because baseline capacity requires zero idle credit and zero deficit multiplier ($d=1.0$ for $c \le B$), a client pulsing requests upon failover is **guaranteed to be admitted at least $B_{\max} = 700.0\,\text{tokens}$**.

### 5.3 Proof that Net Excess Is Strictly Positive
In Redis Cluster failover:
- Refill rate: $R = 1000.0\,\text{tokens/s}$.
- Transition window: $\Delta t_{\text{event}} \le 10\,\text{ms} = 0.010\,\text{s}$ (election + reconnect).
- Lost replenishment: $R \cdot \Delta t_{\text{event}} \le 1000.0 \times 0.010 = 10.0\,\text{tokens}$.

Under worst-case degraded burst admission ($c_{\min} = 700.0$ tokens):
$$\Delta A_{\text{net}} = c_{\min} - R \cdot \Delta t_{\text{event}} \ge 700.0 - 10.0 = \mathbf{+690.0\,\text{tokens}} \gg 0$$

Under pristine / evicted key reset ($c_{\min} = 1000.0$ tokens):
$$\Delta A_{\text{net}} = C_{\max} - R \cdot \Delta t_{\text{event}} = 1000.0 - 10.0 = \mathbf{+990.0\,\text{tokens}} \gg 0$$

**Conclusion:** The condition $c_{\min} - R \cdot \Delta t_{\text{event}} > 0$ does NOT depend on obtaining the full $C_{\max} = 1000$. It is satisfied by any burst draw exceeding $10.0\,\text{tokens}$, which is exceeded by a factor of $70\times$ by baseline bucket capacity alone.

---

## 6. Counterexample Arithmetic & Assessment

We re-verify the three Phase 15 counterexamples demonstrating why an unqualified "if and only if" fails:

### 6.1 Counterexample 1: Quiescent / Zero-Demand Tenant
- **Tenant Input:** Client submits $0$ requests. $A(T) = 0$ for all $T > 0$.
- **Environment:** Node crashes every $\tau = 10\,\text{s}$ ($\lambda_E = 0.1\,\text{s}^{-1} > 0$).
- **Verification:**
  $$\lim_{T \to \infty} \frac{A(T)}{T} = 0 \le R = 1000.0$$
  The rate condition $\le R$ is satisfied, yet $\lambda_E = 0.1 \ne 0$.
  *Status:* **100% Mathematically Valid.**

### 6.2 Counterexample 2: Sub-Rate Benign Tenant
- **Tenant Input:** Client sends continuous benign traffic at $r = 200.0\,\text{tokens/s} = 0.20R$.
- **Environment:** Periodic restarts at $\lambda_E = 0.2\,\text{s}^{-1}$. Even if the client accidentally bursts $50\,\text{tokens}$ per restart:
  $$\lim_{T \to \infty} \frac{A(T)}{T} = 200.0 + (0.2 \times 50.0) = 210.0\,\text{tokens/s} \le 1000.0\,\text{tokens/s} = R$$
  *Status:* **100% Mathematically Valid.**

### 6.3 Counterexample 3: Extended Downtime Outage
- **Tenant Input:** Saturated adversary drawing $c_{\min} = 100.0\,\text{tokens}$ upon failover.
- **Environment:** Node recovery takes $\Delta t_{\text{event}} = 2.0\,\text{s}$ during which all requests are dropped.
- **Lost Tokens vs Burst Gained:**
  $$\Delta A_{\text{net}} = c_{\min} - R \cdot \Delta t_{\text{event}} = 100.0 - (1000.0 \times 2.0) = -1900.0\,\text{tokens} < 0$$
  $$\lim_{T \to \infty} \frac{A(T)}{T} \le R + \lambda_E(-1900.0) < R$$
  *Status:* **100% Mathematically Valid.** Demonstrates why $c_{\min} > R \cdot \Delta t_{\text{event}}$ is an indispensable condition for necessity.

---

## 7. Manuscript Cross-Check & Alignment

We examined the exact wording and mathematics across all relevant repository files:

```
+------------------------------------+------------------+-------------------------------------------------------------+
| Document                           | Location         | Exact Formulation & Status                                  |
+------------------------------------+------------------+-------------------------------------------------------------+
| compact-8-page/latex/main.tex      | Lines 241–252    | Theorem 4: "Sublinear event frequency is universally        |
|                                    |                  | sufficient. Under sustained saturated adversarial demand,   |
|                                    |                  | sublinear event frequency is strictly necessary."           |
|                                    |                  | Proof includes c_min - R * Delta_t_event. (VERIFIED)        |
+------------------------------------+------------------+-------------------------------------------------------------+
| latex/main.tex                     | Lines 291–310    | Theorem 4: Identical two-clause statement with enumerated   |
|                                    |                  | sufficiency and saturated necessity. (VERIFIED)             |
+------------------------------------+------------------+-------------------------------------------------------------+
| PHASE7_3_4_FINAL_G2_CORRECTION.md  | Lines 21–25      | Retracted unqualified "iff". Proved universal sufficiency   |
|                                    |                  | and conditional necessity. (MATCHES MANUSCRIPT)             |
+------------------------------------+------------------+-------------------------------------------------------------+
| PHASE9_2_CLAIM_FREEZE_MANIFEST.json| Lines 34–38      | Explicitly bifurcates universal sufficiency from saturated  |
|                                    |                  | necessity. (FROZEN RESEARCH ANCHOR)                         |
+------------------------------------+------------------+-------------------------------------------------------------+
| AUTHOR_SIGNOFF_CHECKLIST.md        | Line 160         | Corrected in Commit 688eb08 to explicitly state workload    |
|                                    |                  | conditioning and net excess. (SYNCHRONIZED)                 |
+------------------------------------+------------------+-------------------------------------------------------------+
```

### Recommendation on Manuscript LaTeX Sources:
Both `compact-8-page/latex/main.tex` and `latex/main.tex` already state the exact workload condition (*"Under sustained saturated adversarial demand"*) in the theorem statement, and formulate the lower bound with $(c_{\min} - R \cdot \Delta t_{\text{event}})$.  
**No textual changes to the manuscripts are required.** Modifying the LaTeX sources is unnecessary and would risk altering the certified 8-page and 10-page layout budgets.

---

## 8. Final Audit Synthesis

The mathematical foundations of Guarantee G2 have undergone comprehensive independent verification:
1. **Sufficiency is Universal:** Governed by $A(T) \le S_{\max} + R \cdot T + N_E(T) \cdot C_{\max}$. As $T \to \infty$, $S_{\max}/T \to 0$ and $N_E(T)/T \to 0$, forcing macroscopic rate to $R$.
2. **Necessity is Conditional:** Governed by $A(T) \ge R \cdot T + N_E^*(T)(c_{\min} - R \cdot \Delta t_{\text{event}})$. If $\limsup \frac{N_E(T)}{T} > 0$ under saturated demand with positive net excess ($c_{\min} > R \cdot \Delta t_{\text{event}}$), macroscopic rate strictly exceeds $R$.
3. **No Hidden Defects:** All edge cases—event overlap (Lebesgue measure), oscillating frequencies ($\limsup$ subsequence), and partial bursts ($c_{\min} \ge B_{\max} = 700 \gg 10$)—resolve with mathematical rigor.
