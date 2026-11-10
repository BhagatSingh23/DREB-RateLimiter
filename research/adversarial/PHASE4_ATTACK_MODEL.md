# Phase 4 — Adversarial Attack Model Specification

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`f2b0730`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Implementation Source:** [`Phase4AdversarialTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase4AdversarialTest.java)

---

## 1. Mathematical Notation and Preliminaries

Let the limiter state at time $t$ be represented by the tuple:
$$\mathbf{x}(t) = \langle B(t), S(t), I(t), K(t) \rangle \in [0, B_{\max}] \times [0, S_{\max}] \times [0, I_{\max}] \times [0, \infty)$$
where:
- $B(t)$: Token balance in the primary burst bucket.
- $S(t)$: Token balance in the sustained bucket.
- $I(t)$: Stored idle credit.
- $K(t)$: Adaptive abuse penalty state.
- $C$: Contracted burst capacity ($C = 1000$).
- $R$: Sustained contract rate ($R = 1000\,\text{tokens/s}$).
- $\alpha$: Burst capacity allocation fraction ($\alpha = 0.70 \implies B_{\max} = 700$).
- $R_{\text{burst}}$: Burst refill rate ($R_{\text{burst}} = 1666.67\,\text{tokens/s}$).
- $t_{\text{fill}}$: Time required to refill the burst bucket from $0$ to $B_{\max}$:
  $$t_{\text{fill}} = \frac{B_{\max}}{R_{\text{burst}}} = \frac{700}{1666.67} = 0.420\,\text{s}$$
- $I_{\text{eff}}(t)$: Effective idle credit after abuse penalty discount:
  $$I_{\text{eff}}(t) = \frac{I(t)}{1 + \beta K(t)} = \frac{I(t)}{1 + 0.75 K(t)}$$
- $\text{baseDecay}$: Exchange conversion ratio for idle credit consumption ($\text{baseDecay} = 1.5$).
- Admission decision for request with cost $c$ at time $t$:
  $$\text{allow}(c) \iff \left(c \le S(t)\right) \land \left(c \le B(t) + \frac{I_{\text{eff}}(t)}{1.5}\right)$$

---

## 2. Family A: Timing-Aware Credit Farming

### Attack A1: Micro-Pulsing Around Burst Refill Boundary
- **Objective:** Exploit the exact moment idle credit accrual begins ($t > t_{\text{fill}}$) by sending periodic bursts just after the burst bucket fills to $B_{\max}$.
- **Mathematical Specification:**
  $$\tau_{\text{interval}} = t_{\text{fill}} + \delta = 0.420\,\text{s} + 0.050\,\text{s} = 0.470\,\text{s}$$
  $$c_k = 750 \quad \forall k \ge 1, \quad t_k = k \cdot \tau_{\text{interval}}$$
- **Mechanics:** In each interval, $B(t)$ refills to $700$ within $0.420\,\text{s}$. In the remaining $\delta = 0.050\,\text{s}$, idle credit accumulates:
  $$\Delta I = R_{\text{burst}} \cdot \delta = 1666.67 \times 0.050 = 83.33\,\text{tokens}$$
  The request cost $c=750$ consumes the $700$ burst tokens plus $50 \times 1.5 = 75$ idle credits, repeatedly tapping the idle reservoir.

---

### Attack A2: Refill Boundary Jitter
- **Objective:** Evade static timing detection by introducing stochastic uniform jitter around the burst refill threshold.
- **Mathematical Specification:**
  $$t_{k+1} = t_k + t_{\text{fill}} + \epsilon_k, \quad \epsilon_k \sim \mathcal{U}(-0.020\,\text{s}, +0.020\,\text{s})$$
  $$c_k \sim \text{Bernoulli}(p=0.5) \implies c_k \in \{700, 750\}$$
- **Mechanics:** The interval fluctuates within $[0.400\,\text{s}, 0.440\,\text{s}]$. Submissions arriving before $0.420\,\text{s}$ hit an incompletely refilled burst bucket ($B(t) \approx 666.67$), testing limiter stability against rapid jittered deficits.

---

### Attack A3: Periodic Full-Burst Farming
- **Objective:** Exploit the theoretical minimum idle recovery interval ($\tau_{\min} = 0.720\,\text{s}$) required to harvest full burst capacity $C = 1000$ tokens.
- **Mathematical Specification:**
  $$\tau_{\text{interval}} = \tau_{\min} = \frac{B_{\max}}{R_{\text{burst}}} + \frac{1.5(C - B_{\max})}{R_{\text{burst}}} = 0.420\,\text{s} + 0.300\,\text{s} = 0.720\,\text{s}$$
  $$c_k = C = 1000 \quad \forall k \ge 1, \quad t_k = k \cdot 0.720\,\text{s}$$
- **Mechanics:** Repeatedly draws $700$ burst tokens and $300 \times 1.5 = 450$ idle credits, testing whether the adaptive penalty $K(t)$ escalates sufficiently to clamp sustained extraction.

---

### Attack A4: Phase-Shifted Sinusoidal Wait
- **Objective:** Prevent the abuse state $K(t)$ from settling into steady-state limit cycles by modulating the waiting interval across non-harmonic periods.
- **Mathematical Specification:**
  $$t_{k+1} = t_k + \tau(k), \quad \tau(k) = 1.0\,\text{s} + 0.4\sin(0.3 \cdot k)\,\text{s} \in [0.60\,\text{s}, 1.40\,\text{s}]$$
  $$c_k = C = 1000 \quad \forall k \ge 1$$
- **Mechanics:** The adversary dynamically alternates between over-idle recovery intervals ($\tau \approx 1.4\,\text{s}$) and aggressive burst draws ($\tau \approx 0.6\,\text{s}$), probing memory retention of the exponential decay:
  $$K(t_k + \tau) = K(t_k) \cdot e^{-\lambda_{\text{rec}} \tau}$$

---

## 3. Family B: State-Aware Attacks

### Attack B1: White-Box Optimal State Oracle
- **Objective:** Maximize total accepted token throughput with zero rejections by querying the limiter's exact instantaneous capacity.
- **Mathematical Specification:**
  At each discrete time step $t_k = k \cdot 0.50\,\text{s}$:
  $$c^*(t_k) = \max\left(1, \left\lfloor \min\left(S(t_k), B(t_k) + \frac{I_{\text{eff}}(t_k)}{1.5}\right) \right\rfloor\right)$$
- **Mechanics:** Represents the theoretical upper bound of adversarial performance under complete information. The attacker extracts every available token down to single-unit precision while provoking zero 429 errors.

---

### Attack B2: Black-Box Inferred Boundary Probing
- **Objective:** Reconstruct the admission envelope using external feedback without internal state access.
- **Mathematical Specification:**
  At interval $t_k = k \cdot 0.50\,\text{s}$:
  $$c_{k+1} = \begin{cases} 
  \min\left(1000, \lfloor 1.15 \cdot c_k \rfloor + 10\right) & \text{if decision}_k = \text{ALLOW} \\ 
  \max\left(50, \lfloor 0.70 \cdot c_k \rfloor\right) & \text{if decision}_k = \text{DENY} 
  \end{cases}$$
- **Mechanics:** Employs Multiplicative-Increase / Multiplicative-Decrease (MIMD) search to lock onto the dynamic ceiling $B(t) + I_{\text{eff}}(t) / 1.5$.

---

## 4. Family C: Closed-Loop Adaptive Attacks

### Attack C1: AIMD Interval Adaptation
- **Objective:** Dynamically locate the minimum viable waiting time $\tau^*$ for large bursts ($c=800$) using TCP-like congestion control.
- **Mathematical Specification:**
  At request $k$:
  $$\tau_{k+1} = \begin{cases} 
  \max(0.10\,\text{s}, \tau_k - 0.05\,\text{s}) & \text{if decision}_k = \text{ALLOW} \\ 
  \min(3.00\,\text{s}, \tau_k \cdot 1.5) & \text{if decision}_k = \text{DENY} 
  \end{cases}$$
  $$c_k = 800 \quad \forall k$$

---

### Attack C2: Binary Search Cost Adaptation
- **Objective:** Fine-grained binary convergence on the admitted cost ceiling under a fixed interval $\tau = 0.80\,\text{s}$.
- **Mathematical Specification:**
  $$c_{k+1} = \begin{cases} 
  \min(1000, \lfloor 1.15 \cdot c_k \rfloor + 10) & \text{if decision}_k = \text{ALLOW} \\ 
  \max(50, \lfloor 0.70 \cdot c_k \rfloor) & \text{if decision}_k = \text{DENY} 
  \end{cases}$$
  Initial cost: $c_0 = 500\,\text{tokens}$.

---

### Attack C3: Credit Maximizer
- **Objective:** Specifically target maximum credit extraction by synchronizing bursts with the theoretical recovery bound derived in Phase 3.1.
- **Mathematical Specification:**
  $$t_k = k \cdot 0.720\,\text{s}, \quad c_k = 1000$$

---

## 5. Family D: Mixed Legitimate and Adversarial Workloads

### Attack D1: Competing Legitimate Traffic
- **Workload:**
  - **Adversary:** Large periodic bursts $c=1000$ every $\tau = 1.50\,\text{s}$.
  - **Legitimate Client:** Background Poisson stream of micro-requests ($c=50$, mean inter-arrival time $\bar{g} = 0.20\,\text{s} \implies \text{offered load } 250\,\text{tokens/s}$).
- **Target Invariant:** Adversarial credit farming must not cause false rejection ($\text{FRR}_{\text{legit}} \to 0$) for the compliant background stream.

---

### Attack D2: Multi-Worker Concurrent Collision
- **Workload:** 4 unsynchronized worker threads sharing a single client key.
  - Worker $w \in \{0, 1, 2, 3\}$ starts at $t_0 = 0.50 + w \cdot 0.25\,\text{s}$.
  - Worker sends burst $c=500$ at intervals $\tau = 1.0\,\text{s} + \epsilon, \epsilon \sim \mathcal{U}(-0.1\,\text{s}, +0.1\,\text{s})$.
- **Target Invariant:** Simultaneous interleaved burst demands must not cause token balance underflows or state corruption.

---

### Attack D3: Post-Attack Recovery and Collateral Damage
- **Workload:**
  - **Phase 1 ($t \in [0, 30\,\text{s}]$):** Heavy adversarial credit farming ($c=1000$ every $0.720\,\text{s}$), forcing $K(t)$ to high levels.
  - **Phase 2 ($t \in [30\,\text{s}, 60\,\text{s}]$):** Adversary halts completely. A legitimate client arrives at $t=31.0\,\text{s}$, issuing compliant bursts ($c=700$) every $\tau = 1.50\,\text{s}$.
- **Target Invariant:** Legitimate clients arriving after attack cessation must experience rapid recovery ($\text{FRR} = 0\%$) without suffering lingering collateral damage.

---

## 6. Family E: Boundary and Pathological Strategies

### Attack E1: Near-$B_{\max}$ Boundary Probing
- **Workload:** Rapid cycling around the burst allocation boundary: $c_k \in \{699, 700, 701\}$ every $\tau = 0.450\,\text{s}$.
- **Target Invariant:** Strict distinction between zero-credit boundary ($c \le 700$) and 1-credit draw ($c = 701$).

---

### Attack E2: Near-Capacity Boundary Probing
- **Workload:** Cycling around nominal capacity $C$: $c_k \in \{999, 1000, 1001\}$ every $\tau = 0.750\,\text{s}$.

---

### Attack E3: Micro-Cost High-Frequency Flood
- **Workload:** High-frequency stream of $c=1$ requests offered at $1.5 \times R$ ($\Delta t = 1/1500\,\text{s} \approx 0.667\,\text{ms}$).
- **Target Invariant:** Strict rate clamping to $1.000 R$ without credit leakage or arithmetic drift.

---

### Attack E4: Unadmissible Overdraw
- **Workload:** Repeated requests of $c = 1500 > S_{\max} = 1285.71$ every $\tau = 0.50\,\text{s}$.
- **Target Invariant:** Strictly $0\%$ admission, zero state change.

---

### Attack E5: Bimodal Alternation
- **Workload:** Rapid alternation between micro-requests ($c=1$) and maximum bursts ($c=1000$) every $\tau = 0.50\,\text{s}$.
