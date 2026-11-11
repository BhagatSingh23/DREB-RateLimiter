# PHASE 7.3.4: MATHEMATICAL AUDIT OF G2 NECESSITY AND WORKLOAD CONDITIONING

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.4 Task 1  
**Repository Branch:** `research/systematic-improvement`  
**Starting Commit:** `e27b00c`  

---

## 1. Executive Summary

In Phase 7.3.3, the asymptotic rate bound for Guarantee G2 was formulated as:
$$\lim_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le R \iff \lim_{T \to \infty} \frac{N_E(T)}{T} = 0$$

An independent mathematical review reveals that this bidirectional ("if and only if") claim is flawed:
- **Upper Bound Establishes Sufficiency, Not Necessity:** The inequality $A_{\text{total}}(T) \le S_{\max} + R \cdot T + N_E(T) \cdot C_{\max}$ establishes that sublinear event frequency ($\lim \frac{N_E(T)}{T} = 0$) is a **sufficient condition** to guarantee asymptotic compliance under any arbitrary client demand.
- **Why Necessity Fails Under General Workloads:** In an under-utilized system where a client requests tokens at a rate $r \ll R$ (or submits zero requests), $\lim_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le r < R$ will hold even if events occur at a positive frequency $\nu = \lim \frac{N_E(T)}{T} > 0$. Discontinuity events create the *capacity* for over-admission, but do not *force* admissions to occur.
- **The Exact Condition for Necessity:** Sublinear event frequency is necessary **if and only if** the client workload is persistently saturated and adversarial, actively exploiting every reset by consuming at least a fixed lower bound $c_{\min} > 0$ of burst tokens per event while drawing the full sustained rate $R$.

**Definitive Correction:**  
We retract the unqualified "if and only if" claim. We state that $\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$ is a **proven sufficient condition** for universal G2 compliance across all admissible workloads, and is a **necessary condition specifically under a saturated adversarial workload**.

---

## 2. Rigorous Mathematical Proof

Let continuous physical time $T > 0$ be measured in seconds.  
Let $R = 1000.0\,\text{tokens/s}$ be the nominal refill rate.  
Let $S_{\max} = 1285.7143\,\text{tokens}$ be the bounded initial sustained capacity.  
Let $C_{\max} = 1000.0\,\text{tokens}$ be the maximum single-event burst capacity.  
Let $N_E(T) \in \mathbb{N}_0$ be the cumulative count of isolated discontinuity events occurring in $[0, T]$.  
*(Note: $N_E(T)$ is strictly decoupled from the continuous abuse score $K \ge 0$).*

### 2.1 Theorem 1 (Universal Sufficiency)
*For any client workload, if the discontinuity event frequency is sublinear:*
$$\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$$
*then the asymptotic admission rate is strictly bounded by the nominal refill rate:*
$$\limsup_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le R = 1000.0\,\text{tokens/s}$$

**Proof:**
Total cumulative admissions are bounded by:
$$A_{\text{total}}(T) \le S_{\max} + R \cdot T + N_E(T) \cdot C_{\max}$$
Dividing by $T$:
$$\frac{A_{\text{total}}(T)}{T} \le \frac{S_{\max}}{T} + R + C_{\max} \cdot \frac{N_E(T)}{T}$$
Taking the limit superior as $T \to \infty$:
$$\limsup_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le R + \lim_{T \to \infty} \frac{S_{\max}}{T} + C_{\max} \cdot \lim_{T \to \infty} \frac{N_E(T)}{T}$$
Because $S_{\max}$ is finite, $\lim_{T \to \infty} \frac{S_{\max}}{T} = 0$.  
By hypothesis, $\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$.  
Therefore:
$$\limsup_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le R \quad \blacksquare$$

---

### 2.2 Theorem 2 (Conditional Necessity Under Saturated Demand)
*Suppose a client workload satisfies the Saturated Adversarial Demand criteria:*
1. *Full Sustained Consumption:* The client submits sufficient sustained demand to consume all continuously accrued refill: $\int_0^T r_{\text{accrued}}(t)\,dt = R \cdot T$.
2. *Persistent Burst Exploitation:* Across every discontinuity event $k \in \{1, \dots, N_E(T)\}$, the client submits a burst request drawing at least $c_k \ge c_{\min} > 0$ tokens.

*Under this workload, the condition $\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$ is strictly necessary for $\lim_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \le R$.*

**Proof:**
Under saturated demand, total admissions satisfy the lower bound:
$$A_{\text{total}}(T) \ge R \cdot T + \sum_{k=1}^{N_E(T)} c_k \ge R \cdot T + N_E(T) \cdot c_{\min}$$
Dividing by $T$:
$$\frac{A_{\text{total}}(T)}{T} \ge R + c_{\min} \cdot \frac{N_E(T)}{T}$$
Taking the limit as $T \to \infty$:
$$\liminf_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \ge R + c_{\min} \cdot \lim_{T \to \infty} \frac{N_E(T)}{T}$$
Suppose for contradiction that events occur with positive asymptotic frequency:
$$\lim_{T \to \infty} \frac{N_E(T)}{T} = \nu > 0$$
Then:
$$\liminf_{T \to \infty} \frac{A_{\text{total}}(T)}{T} \ge R + c_{\min} \cdot \nu > R$$
This strictly violates G2 ($\lim \frac{A}{T} \le R$).  
Therefore, under saturated adversarial demand, $\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$ is strictly necessary. $\blacksquare$

---

## 3. Retraction and Synthesis

| Mathematical Statement | Validity | Justification |
| :--- | :---: | :--- |
| $\lim \frac{N_E(T)}{T} = 0 \implies \lim \frac{A(T)}{T} \le R$ | **`PROVEN (UNIVERSAL)`** | Holds unconditionally for all client workloads. |
| $\lim \frac{A(T)}{T} \le R \implies \lim \frac{N_E(T)}{T} = 0$ | **`INVALID (GENERAL WORKLOAD)`** | Fails for benign or quiescent client workloads where events occur but extra tokens are unconsumed. |
| $\lim \frac{A(T)}{T} \le R \iff \lim \frac{N_E(T)}{T} = 0$ | **`PROVEN (SATURATED WORKLOAD ONLY)`** | Requires explicit assumption of persistent burst exploitation ($c_k \ge c_{\min} > 0$). |

### Definitive Formulation for the Research Contract
> **Guarantee G2 (Sustained Throughput Bound):**  
> Sublinear discontinuity frequency ($\lim_{T \to \infty} \frac{N_E(T)}{T} = 0$) is a **proven sufficient condition** ensuring that long-term average throughput does not exceed nominal refill rate $R = 1000.0\,\text{tokens/s}$ for any admissible workload.  
> Furthermore, it is a **necessary condition** to prevent rate violation under worst-case saturated adversarial traffic.
