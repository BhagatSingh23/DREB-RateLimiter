# PHASE 15: FINAL G2 MATHEMATICAL NECESSITY AUDIT
## Long-Run Rate Envelope and Sublinear Discontinuity-Event Frequency

**Repository:** `BhagatSingh23/DREB-RateLimiter`  
**Branch:** `submission/nice-teas-asia-2026`  
**Base Commit:** `65e7ca7`  
**Audit Date:** 14 November 2026  
**Auditor:** Quality Control & Formal Verification Panel (Phase 15 Audit)  
**Subject:** Mathematical Correctness of Guarantee G2 (Theorem 4 in Manuscripts, Theorems 4 & 5 in Manifest)  

---

## 1. Executive Summary & Verdict

This audit rigorously evaluates the mathematical validity of Guarantee G2 concerning long-run macroscopic rate convergence:

$$\lim_{T \to \infty} \frac{A(T)}{T} \le R$$

and its relationship with the asymptotic frequency of state-discontinuity events:

$$\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$$

### Definitive Audit Findings:
1. **Unqualified Equivalence is Mathematically Invalid:**  
   The bidirectional statement $\lim_{T \to \infty} \frac{N_E(T)}{T} = 0 \iff \lim_{T \to \infty} \frac{A(T)}{T} \le R$ reported as shorthand in the Phase 14 checklist summary table is **FALSE** in the general case. A tenant submitting sub-SLA demand or zero requests trivially satisfies $\lim \frac{A(T)}{T} \le R$ even under high-frequency periodic failovers ($\lambda_E > 0$).
2. **Manuscript Formulation is Workload-Conditioned:**  
   In both the 8-page (`compact-8-page/latex/main.tex`) and 10-page (`latex/main.tex`) manuscripts, Theorem 4 **does not claim an unqualified equivalence**. The theorem explicitly restricts the necessity direction:
   > *"Sublinear event frequency ($\lim_{T\to\infty} \frac{N_E(T)}{T} = 0$) is universally sufficient. Under sustained saturated adversarial demand, sublinear event frequency is strictly necessary."*
3. **Explicit Parameter Assumption for Strict Necessity:**  
   The proof's lower-bound inequality $\lim_{T \to \infty} \frac{A(T)}{T} \ge R + \lambda_E(c_{\min} - R \cdot \Delta t_{\text{event}}) > R$ requires the net excess per event to be positive: $c_{\min} > R \cdot \Delta t_{\text{event}}$. In DREB-RCA70, $C_{\max} = 1000.0\,\text{tokens}$ and failover duration is bounded by $\Delta t_{\text{event}} \le 10\,\text{ms}$, yielding $R \cdot \Delta t_{\text{event}} \le 10.0\,\text{tokens} \ll C_{\max}$, which guarantees $c_{\min} - R \cdot \Delta t_{\text{event}} = +990.0\,\text{tokens} > 0$.
4. **Errata & Correction Executed:**  
   The misleading `\iff` shorthand in `AUTHOR_SIGNOFF_CHECKLIST.md` Section 7 has been corrected to explicitly state the workload-conditioned necessity.

### Formal Audit Verdict:
```
+-------------------------------------------------------------------------------------------------------+
| VERDICT: VERIFIED UNDER EXPLICIT ASSUMPTIONS                                                          |
|                                                                                                       |
| 1. Sufficiency Direction: UNCONDITIONALLY AND UNIVERSALLY VALID for all arrival patterns.            |
| 2. Necessity Direction:   VALID IF AND ONLY IF evaluated under Saturated Adversarial Demand with      |
|                           positive net event excess (c_min > R * Delta_t_event).                      |
| 3. Unqualified Equivalence: RETRACTED AND CORRECTED in the signoff checklist.                        |
+-------------------------------------------------------------------------------------------------------+
```

---

## 2. Concordance Across Canonical Sources

The exact formulation of Guarantee G2 was tracked and compared across all canonical repositories and documents:

| Source Document | Formulation of Guarantee G2 | Classification |
| :--- | :--- | :---: |
| **`PHASE7_3_2_G2_ASSUMPTIONS.md`** (Historical Phase 7.3.2) | $\lim \frac{A(T)}{T} \le R \iff \lim \frac{K(T)}{T} = 0$ | *Unqualified "iff" (Later Retracted)* |
| **`PHASE7_3_4_FINAL_G2_CORRECTION.md`** (Historical Phase 7.3.4) | Retracted unqualified "iff". Proved universal sufficiency and conditional necessity under saturated demand. | **Canonical Mathematical Origin** |
| **`PHASE9_2_CLAIM_FREEZE_MANIFEST.json`** (Frozen Manifest) | `sufficiency: "lim(T->inf) N_E(T)/T = 0 is universally sufficient under all admissible demand patterns"`; `necessity: "lim(T->inf) N_E(T)/T = 0 is strictly necessary under sustained saturated adversarial demand"` | **Canonical Research Baseline** |
| **`compact-8-page/latex/main.tex`** (8-Page Manuscript, Line 246) | *"Sublinear event frequency ($\lim_{T\to\infty} \frac{N_E(T)}{T} = 0$) is universally sufficient. Under sustained saturated adversarial demand, sublinear event frequency is strictly necessary."* | **Conditioned (Accurate)** |
| **`latex/main.tex`** (10-Page Manuscript, Line 297) | Identical two-part conditioned theorem statement with enumerated sufficiency and necessity clauses. | **Conditioned (Accurate)** |
| **`AUTHOR_SIGNOFF_CHECKLIST.md`** (Phase 14 Signoff, Line 160) | `\lim N_E(T)/T = 0 \iff \lim A(T)/T \le R` | *Misleading shorthand (Corrected in Phase 15)* |

---

## 3. Rigorous Independent Derivations

### 3.1 Theorem 1: Universal Sufficiency of Sublinear Event Frequency

**Hypothesis:**  
Let continuous observation time $T > 0$.  
Let nominal gateway refill rate be $R = 1000.0\,\text{tokens/s}$.  
Let sustained capacity bound be $S_{\max} = 1285.7143\,\text{tokens}$.  
Let maximum single-event burst capacity be $C_{\max} = 1000.0\,\text{tokens}$.  
Let $N_E(T) \in \mathbb{N}_0$ be the cumulative count of isolated state-discontinuity events (failovers with un-replicated state loss, container restarts, or clock discontinuities) occurring in $[0, T]$.  
Assume:
$$\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$$

**Derivation:**  
By Theorem 1 ($G_1$), between consecutive discontinuity events $i-1$ and $i$, admitted tokens cannot exceed continuous sustained replenishment $R \cdot \Delta t_i$.  
At each discontinuity event $i$, state reset can admit at most the maximum allowable burst capacity $C_{\max} = B_{\max} + I_{\max}/d = 1000.0\,\text{tokens}$.  
Therefore, for any arrival sequence over $[0, T]$, cumulative admissions are bounded by:
$$A(T) \le S_{\max} + R \cdot T + \sum_{i=1}^{N_E(T)} C_{\max} = S_{\max} + R \cdot T + N_E(T) \cdot C_{\max}$$

Dividing both sides by $T > 0$:
$$\frac{A(T)}{T} \le \frac{S_{\max}}{T} + R + C_{\max} \cdot \frac{N_E(T)}{T}$$

Taking the limit superior as $T \to \infty$:
$$\limsup_{T \to \infty} \frac{A(T)}{T} \le R + \lim_{T \to \infty} \frac{S_{\max}}{T} + C_{\max} \cdot \lim_{T \to \infty} \frac{N_E(T)}{T}$$

Because $S_{\max}$ and $C_{\max}$ are finite constants:
$$\lim_{T \to \infty} \frac{S_{\max}}{T} = 0$$
$$\lim_{T \to \infty} \left( C_{\max} \cdot \frac{N_E(T)}{T} \right) = C_{\max} \cdot 0 = 0$$

Therefore:
$$\limsup_{T \to \infty} \frac{A(T)}{T} \le R = 1000.0\,\text{tokens/s} \quad \blacksquare$$

**Universal Scope:**  
This upper bound holds for **all** arrival patterns, whether benign, quiescent, bursty, or adversarial, requiring zero assumptions regarding client demand.

---

### 3.2 Theorem 2: Conditional Necessity Under Saturated Adversarial Demand

**Hypothesis:**  
Assume client demand satisfies the **Saturated Adversarial Demand Criteria**:
1. **Full Continuous Refill Absorption:** The adversary continuously submits requests at or above rate $R$, keeping the sustained bucket empty ($S(t) = 0$) and consuming all continuously replenished tokens:
   $$\int_0^{T_{\text{normal}}} r(t)\,dt \ge R \cdot T_{\text{normal}}$$
2. **Persistent Discontinuity Burst Exploitation:** Across every discontinuity event $i \in \{1, \dots, N_E(T)\}$, the adversary immediately submits a batch burst drawing at least:
   $$A_{\text{event}}(i) \ge c_{\min} > 0$$
3. **Positive Net Event Excess:** The burst consumed during event $i$ strictly exceeds the nominal tokens that would have replenished over the event transition window $\Delta t_{\text{event}}$:
   $$c_{\min} > R \cdot \Delta t_{\text{event}} \iff \Delta A_{\text{net}} \equiv c_{\min} - R \cdot \Delta t_{\text{event}} > 0$$

**Derivation:**  
Let $T_{\text{normal}} = T - N_E(T) \cdot \Delta t_{\text{event}}$ be the cumulative time during which continuous replenishment operates normally.  
Total admitted tokens across $[0, T]$ satisfy the lower bound:
$$A(T) \ge R \cdot T_{\text{normal}} + \sum_{i=1}^{N_E(T)} A_{\text{event}}(i)$$
$$A(T) \ge R(T - N_E(T) \cdot \Delta t_{\text{event}}) + N_E(T) \cdot c_{\min}$$
$$A(T) \ge R \cdot T + N_E(T) \cdot (c_{\min} - R \cdot \Delta t_{\text{event}})$$

Dividing both sides by $T > 0$:
$$\frac{A(T)}{T} \ge R + \frac{N_E(T)}{T} \cdot (c_{\min} - R \cdot \Delta t_{\text{event}})$$

Now assume for contradiction that the asymptotic discontinuity event frequency is strictly positive:
$$\lim_{T \to \infty} \frac{N_E(T)}{T} = \lambda_E > 0$$

Taking the limit inferior as $T \to \infty$:
$$\liminf_{T \to \infty} \frac{A(T)}{T} \ge R + \lambda_E \cdot (c_{\min} - R \cdot \Delta t_{\text{event}})$$

Because $\lambda_E > 0$ and $c_{\min} - R \cdot \Delta t_{\text{event}} > 0$:
$$\lambda_E \cdot (c_{\min} - R \cdot \Delta t_{\text{event}}) > 0$$

Therefore:
$$\liminf_{T \to \infty} \frac{A(T)}{T} > R$$

This strictly violates Guarantee G2 ($\lim_{T \to \infty} \frac{A(T)}{T} \le R$).  
Hence, by contraposition, $\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$ is **strictly necessary** under saturated adversarial demand. $\blacksquare$

---

## 4. Mathematical Counterexamples to Unqualified Necessity

To demonstrate why an unqualified bidirectional equivalence ($\iff$) is invalid, we present three explicit counterexamples:

### Counterexample 1: Quiescent / Zero-Demand Tenant
- **Tenant Behavior:** A tenant key submits zero requests: $A(T) = 0$ for all $T \ge 0$.
- **Environment:** Infrastructure misconfiguration causes primary failovers every $\tau = 10\,\text{s}$, yielding $\lambda_E = 0.1\,\text{s}^{-1} > 0$.
- **Rate Outcome:**
  $$\lim_{T \to \infty} \frac{A(T)}{T} = 0 \le R$$
- **Contradiction:** The long-run rate envelope $\le R$ is satisfied, yet $\lim \frac{N_E(T)}{T} = 0.1 \ne 0$.  
  **Conclusion:** Sublinear event frequency is NOT necessary for a quiescent tenant.

### Counterexample 2: Low-Rate Benign Tenant
- **Tenant Behavior:** A client submits steady Poisson traffic at nominal rate $r_{\text{in}} = 200.0\,\text{tokens/s} = 0.20R$.
- **Environment:** Periodic pod restarts occur every $5\,\text{seconds}$ ($\lambda_E = 0.2\,\text{s}^{-1}$).
- **Admission Outcome:** Even if the tenant happens to admit an extra $50\,\text{tokens}$ per restart:
  $$\lim_{T \to \infty} \frac{A(T)}{T} = 200.0 + (0.2 \times 50.0) = 210.0\,\text{tokens/s} \le 1000.0\,\text{tokens/s} = R$$
- **Contradiction:** The long-run rate is strictly bounded below $R$, despite non-zero event frequency $\lambda_E = 0.2$.  
  **Conclusion:** Sublinear event frequency is NOT necessary for sub-saturated tenants.

### Counterexample 3: Extended Downtime Discontinuity ($c_{\min} \le R \cdot \Delta t_{\text{event}}$)
- **Tenant Behavior:** Saturated adversary attempting to burst upon failover ($c_{\min} = 100\,\text{tokens}$).
- **Environment:** Server failover takes $\Delta t_{\text{event}} = 2.0\,\text{seconds}$ of hard downtime during which the gateway rejects all traffic.
- **Net Token Balance per Event:**
  $$c_{\min} - R \cdot \Delta t_{\text{event}} = 100 - (1000 \times 2.0) = -1900.0\,\text{tokens} < 0$$
- **Rate Outcome:**
  $$\lim_{T \to \infty} \frac{A(T)}{T} \le R + \lambda_E(-1900.0) < R$$
- **Contradiction:** Discontinuity events *suppressed* rather than elevated throughput because downtime lost more replenishment than the burst regained.  
  **Conclusion:** Necessity requires the positive net excess condition: $c_{\min} > R \cdot \Delta t_{\text{event}}$.

---

## 5. Parameter Validation for DREB-RCA70

Does the condition $c_{\min} > R \cdot \Delta t_{\text{event}}$ hold in the evaluated DREB-RCA70 system?

1. **Adversarial Burst Potential ($c_{\min}$):**  
   Under un-replicated failover or pristine reset, state resets to initial burst capacity:
   $$C_{\max} = B_{\max} + \frac{I_{\max}}{d} = 700 + \frac{450}{1.5} = 1000.0\,\text{tokens}$$
   A saturated adversary pulses an immediate batch of cost $C_{\max}$, so $c_{\min} = 1000.0\,\text{tokens}$.
2. **Failover Duration ($\Delta t_{\text{event}}$):**  
   Under Redis Cluster failover with election timeout and client reconnect, Section IV-C and Theorem 3 establish $\Delta t_{\text{event}} \le 10\,\text{ms} = 0.010\,\text{s}$.
3. **Refill Over Transition:**  
   $$R \cdot \Delta t_{\text{event}} = 1000.0 \times 0.010 = 10.0\,\text{tokens}$$
4. **Net Excess Admission per Event:**  
   $$\Delta A_{\text{net}} = C_{\max} - R \cdot \Delta t_{\text{event}} = 1000.0 - 10.0 = \mathbf{+990.0\,\text{tokens}} > 0$$

Furthermore, against the steady-state sustained bucket $S_{\max} = 1285.7143$, Theorem 3 proves that net single-event excess is $\text{Excess}_{G_1} = +704.2857\,\text{tokens} > 0$.  
Therefore, the positive net excess assumption is **strictly satisfied** by the DREB-RCA70 system architecture.

---

## 6. Property-Based Test Verification

To empirically verify that finite-time property tests remain green and consistent with the formal proof, the jqwik property-based test suite was executed:

```bash
cd /Users/cruise/Desktop/DREB-RateLimiter && mvn test -Dtest=DrebRcaPropertiesTest
```

### Execution Results:
- `cumulativeAdmissionBoundG1Holds`: **1,000 / 1,000 checks passed** (0 failures).
- `theorem2FiniteHorizonBoundHolds`: **1,000 / 1,000 checks passed** (0 failures).
- `stateBoundsHold`: **1,000 / 1,000 checks passed** (0 failures).
- `fullPostIdleBurstAdmitted`: **Passed**.
- **Total Tests:** 5, **Failures:** 0, **Errors:** 0.

*(Note: In accordance with audit standards, property-based tests are treated as regression sanity checks and not as substitutes for the mathematical proofs derived in Section 3).*

---

## 7. Actions Taken & File Synchronization

1. **`AUTHOR_SIGNOFF_CHECKLIST.md` Corrected:**  
   Line 160 was updated from the unqualified `\iff` to:
   ```markdown
   | **Theorem 4 ($G_2$ Sufficiency/Necessity)** | $\lim \frac{N_E(T)}{T} = 0 \implies \lim \frac{A(T)}{T} \le R$ (Universal Sufficiency); necessity holds under saturated adversarial demand ($c_{\min} > R \cdot \Delta t_{\text{event}}$) | Eq. (9): Formally proven under explicit workload assumptions (Phase 15 Audit) | **VERIFIED UNDER EXPLICIT ASSUMPTIONS** |
   ```
2. **Manuscripts Audited (`main.tex` and `compact-8-page/latex/main.tex`):**  
   Both manuscripts already explicitly state *"Under sustained saturated adversarial demand, sublinear event frequency is strictly necessary"* in Theorem 4, and derive the exact lower bound with $(c_{\min} - R \cdot \Delta t_{\text{event}})$. No modification to the manuscripts' frozen text was required.
3. **Audit Artifact Created:**  
   `submission/nice-teas-asia-2026/final_audit/PHASE15_G2_NECESSITY_AUDIT.md` (this document).

---

## 8. Definitive Audit Conclusion

Guarantee G2 is **MATHEMATICALLY SOUND AND RIGOROUSLY VERIFIED UNDER EXPLICIT ASSUMPTIONS**.

The apparent contradiction raised by the shorthand notation in the Phase 14 summary table has been resolved:
- The universal claim applies strictly to **Sufficiency**.
- **Necessity** is mathematically established under the three explicit assumptions of (1) full sustained refill absorption, (2) persistent burst exploitation, and (3) positive net event excess ($c_{\min} > R \cdot \Delta t_{\text{event}}$).
- The submission manuscripts correctly reflect these conditions.
