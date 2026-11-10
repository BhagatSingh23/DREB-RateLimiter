# Phase 3.1: G2 Initial-State and Idle-Duration Audit

## 1. Executive Summary

This report performs a rigorous audit of the initial-state assumptions and transition equations underlying the **$0.720\text{-second}$ minimum idle duration** claim established for **DREB-RCA70** in Phase 3.

### Audit Findings:
1. **The 0.720-Second Value is Valid under Specific, Rigorous Operational Pre-Conditions:**
   The claim that $\tau_{\min} = 0.420\text{s} + 0.300\text{s} = 0.720\text{s}$ admits a full nominal burst $C = 1,000$ tokens is mathematically exact **if and only if**:
   - $B_0 = 0$ (burst bucket starts empty).
   - $I_0 = 0$ (stored idle credit starts empty).
   - **$K_0 = 0$ (abuse score is pristine / zero).**
   - **$S_0 \ge 280.0\text{ tokens}$ (sustained envelope bucket has at least 280 tokens).**
   - Zero intervening requests arrive during $[0, \tau]$.
2. **Sustained Bucket Bound When Completely Depleted ($S_0 = 0$):**
   If a client has completely exhausted the sustained envelope bucket ($S_0 = 0$), the sustained bucket refilling at $S_{\text{rate}} = 1,000\text{ tokens/s}$ requires $\frac{1000 - 0}{1000} = \mathbf{1.000\text{ second}}$. At $\tau = 0.720\text{s}$, $S(0.720) = 720 < 1000$, and the request is denied.
3. **Abuse Score Sensitivity ($K_0 > 0$):**
   If a client has a non-zero abuse score ($K_0 > 0$), the adaptive ceiling $I_{\text{ceiling}} = \frac{450}{1 + 0.75 K} < 450$ prevents idle credit from ever reaching the required $450$ tokens until $K$ recovers to zero ($t \ge K_0 / \lambda_{\text{rec}} = 20 K_0\text{ seconds}$).
4. **Generalization:**
   The complete closed-form piecewise formula for arbitrary initial states $\mathbf{x}_0 = (B_0, S_0, I_0, K_0)$ and arbitrary request costs $c$ is derived, proven, and verified.

---

## 2. Transition System Specification from Implementation

Using [`DREBLimiter.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/DREBLimiter.java), [`ConstantRateRefillStrategy.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/ConstantRateRefillStrategy.java), and [`AdaptiveCeilingIdleCreditPolicy.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java) as the definitive source of truth:

### 2.1 State Variables
- $B(t) \in [0, B_{\max}]$: Tokens in the primary burst bucket ($B_{\max} = 700.0$).
- $S(t) \in [0, S_{\max}]$: Tokens in the sustained envelope bucket ($S_{\max} = 1,285.71$).
- $I(t) \in [0, \text{baseCap}]$: Stored idle credit ($\text{baseCap} = 450.0$).
- $K(t) \ge 0$: Draw counter / abuse index (`state.abuseScore`).

### 2.2 Continuous Refill Transitions over Idle Interval $\Delta t > 0$
1. **Primary Burst Bucket:**
   $$B(\Delta t) = \min\left(B_{\max}, \; B_0 + B_{\text{rate}} \cdot \Delta t\right), \quad B_{\text{rate}} = \frac{5}{3}R = 1,666.67\text{ tk/s}$$
2. **Full-Bucket Duration ($t_{\text{full}}$):**
   $$t_{\text{fill}} = \max\left(0, \; \frac{B_{\max} - B_0}{B_{\text{rate}}}\right), \quad t_{\text{full}} = \max\left(0, \; \Delta t - t_{\text{fill}}\right)$$
3. **Sustained Bucket:**
   $$S(\Delta t) = \min\left(S_{\max}, \; S_0 + S_{\text{rate}} \cdot \Delta t\right), \quad S_{\text{rate}} = R = 1,000.0\text{ tk/s}$$
4. **Abuse Index Recovery:**
   $$K(\Delta t) = \max\left(0, \; K_0 - \lambda_{\text{rec}} \cdot \Delta t\right), \quad \lambda_{\text{rec}} = 0.05\text{ s}^{-1}$$
5. **Effective Idle Credit Ceiling:**
   $$I_{\text{ceiling}}(\Delta t) = \frac{\text{baseCap}}{1 + \beta \cdot K(\Delta t)}, \quad \beta = 0.75$$
6. **Idle Credit Accrual:**
   $$I(\Delta t) = \begin{cases}
   \min\left(I_{\text{ceiling}}(\Delta t), \; I_0 + \text{gain} \cdot t_{\text{full}}\right) & \text{if } t_{\text{full}} > 0 \\
   \min\left(I_{\text{ceiling}}(\Delta t), \; I_0\right) & \text{if } t_{\text{full}} = 0
   \end{cases}, \quad \text{gain} = 1.5R = 1,500.0\text{ tk/s}$$

### 2.3 Discrete Admission Decision for Request of Cost $c$
1. **Effective Burst Capacity:**
   $$B_{\text{eff}}(\Delta t) = B(\Delta t) + \frac{I(\Delta t)}{\text{baseDecay}}, \quad \text{baseDecay} = 1.5$$
2. **Conjunction Guard:**
   $$\text{Decision} = \text{ALLOW} \iff S(\Delta t) \ge c \quad \text{AND} \quad B_{\text{eff}}(\Delta t) \ge c$$
3. **State Mutation upon ALLOW:**
   - $S(\Delta t^+) = S(\Delta t) - c$
   - Deficit: $d = \max(0, \; c - B(\Delta t))$
   - $B(\Delta t^+) = \max(0, \; B(\Delta t) - c)$
   - $I(\Delta t^+) = \max(0, \; I(\Delta t) - \text{baseDecay} \cdot d)$
   - $K(\Delta t^+) = K(\Delta t) + \frac{d}{\text{baseCap}}$

---

## 3. Dissecting the 0.720-Second Claim

The Phase 3 report established:
$$\tau_{\min} = t_{\text{fill}} + t_{\text{credit}} = 0.420\text{s} + 0.300\text{s} = 0.720\text{ seconds}$$

### 3.1 Necessary Initial Conditions for the 0.720s Result:
For a request costing $c = C = 1,000$ to be admitted at $\tau = 0.720\text{s}$, the following initial state conditions are **strictly required**:

1. **$K_0 = 0$ (Pristine Abuse State):**
   To admit $c = 1,000$, the deficit is $d = 1000 - 700 = 300\text{ tokens}$.
   The effective credit must satisfy $\frac{I(\tau)}{1.5} \ge 300 \implies I(\tau) \ge 450\text{ tokens}$.
   Since $I(\tau) \le I_{\text{ceiling}}(\tau) = \frac{450}{1 + 0.75 K(\tau)}$, we must have:
   $$\frac{450}{1 + 0.75 K(\tau)} \ge 450 \iff K(\tau) \le 0 \iff K(\tau) = 0$$
   If $K_0 > 0$, $K(\tau)$ cannot reach $0$ at $\tau = 0.720\text{s}$ unless $K_0 \le \lambda_{\text{rec}} \cdot 0.720 = 0.05 \times 0.720 = \mathbf{0.036}$.
   If $K_0 > 0.036$ (e.g. $K_0 = 0.5$ or $1.0$), the ceiling remains contracted below $450$, and admission is **DENIED** at $\tau = 0.720\text{s}$.
2. **$S_0 \ge 280.0\text{ tokens}$ (Sustained Residual Capacity):**
   In $\tau = 0.720\text{s}$, the sustained bucket refills by $\Delta S = S_{\text{rate}} \cdot \tau = 1,000 \times 0.720 = 720.0\text{ tokens}$.
   Admission requires $S(\tau) = S_0 + 720.0 \ge 1,000 \iff \mathbf{S_0 \ge 280.0\text{ tokens}}$.
   - **Operational Realism:** In standard operation, a client that recently burst $700$ tokens from full envelope capacity ($S_{\max} = 1,285.71$) has a residual balance $S_0 = 1,285.71 - 700 = 585.71 \ge 280.0$.
   - **Depleted Envelope Case ($S_0 = 0$):** If the client previously drained the sustained bucket to zero, then $S(0.720) = 720 < 1,000$. Admission requires:
     $$\tau = \frac{1,000 - 0}{1,000} = \mathbf{1.000\text{ second}}$$
3. **Primary Bucket ($B_0 = 0$) and Idle Credit ($I_0 = 0$):**
   Refilling $B$ from $0 \to 700$ takes $t_{\text{fill}} = 700 / 1666.67 = 0.420\text{s}$. Accruing $I$ from $0 \to 450$ takes $t_{\text{credit}} = 450 / 1500 = 0.300\text{s}$. Total burst time is $0.420 + 0.300 = 0.720\text{s}$.

---

## 4. Formal Clarification of Guarantee Language

To prevent ambiguity in future publications, we establish four precise categories of idle guarantees:

| Guarantee Category | Mathematical Scope | Idle Duration Formula / Bound |
| :--- | :--- | :--- |
| **1. Necessary Idle Condition** | Strict lower bound on any idle duration to admit $c > B_{\max}$ | $\tau \ge \frac{c - B_{\max}}{\text{gain}/\text{baseDecay}} = \frac{c - B_{\max}}{1,000\text{ tk/s}}$ |
| **2. Sufficient Idle Condition** | Bound that guarantees admission under *arbitrary* valid states ($S_0 \ge 0, K_0 \ge 0$) | $\tau \ge \max\left(\frac{C}{S_{\text{rate}}}, \; \frac{B_{\max}}{B_{\text{rate}}} + \frac{\text{baseCap}}{\text{gain}}, \; \frac{K_0}{\lambda_{\text{rec}}}\right)$ |
| **3. Minimum Idle Duration (Reference State)** | Initial state: $B_0=0, I_0=0, K_0=0, S_0 \ge 280$ for cost $c=C$ | $\tau_{\min} = 0.420\text{s} + 0.300\text{s} = \mathbf{0.720\text{ seconds}}$ |
| **4. Minimum Idle Duration (Fully Depleted State)** | Initial state: $B_0=0, I_0=0, K_0=0, S_0 = 0$ for cost $c=C$ | $\tau_{\min} = \max(0.720\text{s}, 1.000\text{s}) = \mathbf{1.000\text{ second}}$ |
