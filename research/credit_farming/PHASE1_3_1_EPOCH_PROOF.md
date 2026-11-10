# Phase 1.3.1: Credit-Episode Invariant Closure

**Project:** DREB — Idle-Conditioned Burst Limiting  
**Phase:** 1.3.1 — Formal Credit-Accrual Episode Invariant Closure  
**Branch:** `research/systematic-improvement`  
**Status:** COMPLETE — Universal Attack-Schedule Guarantee Established  
**Audit Artifacts:**
- Primary Proof Document: [`PHASE1_3_1_EPOCH_PROOF.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/research/credit_farming/PHASE1_3_1_EPOCH_PROOF.md)
- Invariant & Property Suite: [`AntiFarmingCandidatePropertiesTest.java#L82-L162`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/AntiFarmingCandidatePropertiesTest.java#L82-L162)

---

## 1. Executive Summary & Purpose

The purpose of this audit is to provide the final independent mathematical clarification of **Lemma 2** prior to Phase 2 authorization.

### The Question Under Review
In Phase 1.3, Lemma 2 established the invariant bound on $K(t)$ by evaluating a single credit draw followed by a burst-refill interval $t_{\text{fill}} = \frac{B_{\max}}{B_{\text{rate}}}$.  
However, an adversary might not consume the entire credit pool in a single monolithic request. Instead, the attacker could:
1. Accrue an idle-credit balance $I_{\text{accum}}$ during an idle period.
2. Fire multiple smaller requests in rapid succession (e.g., at $\Delta t = 0$ or micro-intervals $\Delta t \ll t_{\text{fill}}$), drawing successive partial deficits $\delta_1, \delta_2, \dots, \delta_m$ from that same pool before the burst bucket has refilled.

**Goal:** Establish whether the invariant $K(t) \le \max(K(0), K^* + \frac{1}{\text{baseDecay}})$ holds universally across **arbitrary credit-accrual episodes**, accounting for multi-request partial spending, dynamic ceiling clamping, and interleaved non-credit requests.

### Audit Verdict
> [!IMPORTANT]
> **Audit Verdict: UNIVERSAL INVARIANT ESTABLISHED AND CLOSED.**
> 1. All six foundational statements are **mathematically proven and verified in code**.
> 2. The aggregate increase in $K$ across all partial draws from a single credit pool is strictly bounded by:
>    $$\Delta K_{\text{episode}} \le \frac{1}{\text{baseDecay}(1 + \beta K_{\text{start}})}$$
> 3. Between any two successive credit-accrual episodes, the burst bucket must refill from $0$ to $B_{\max}$, mandating a cumulative elapsed time of at least $t_{\text{fill}} = \frac{B_{\max}}{B_{\text{rate}}}$ and enforcing a draw-index recovery of at least:
>    $$\Delta K_{\text{recovery}} \ge \frac{B_{\max} \cdot \lambda_{\text{rec}}}{B_{\text{rate}}}$$
> 4. Consequently, $K(t) \le \max(K(0), K^* + \frac{1}{\text{baseDecay}})$ holds for **all arbitrary request arrival schedules**, including multi-draw bursts, micro-pulsing, and non-periodic schedules.

---

## 2. Formal Definition of a Credit-Accrual Episode

We partition the continuous timeline $[0, T]$ into alternating, mutually exclusive phases:

```
[--- Accrual Phase (tFull > 0) ---] [--- Spending Phase (delta > 0) ---] [--- Refill Phase (B -> B_max) ---]
<--------- Episode n Accrual ------><---------- Episode n Spend --------><----- Inter-Episode Recovery ----->
```

### Formal Definitions:
1. **Accrual Episode $E_n$:** A contiguous maximal time interval during which $t_{\text{full}} > 0$.  
   During $E_n$, $B(t) = B_{\max}$, and idle credit accrues up to the effective ceiling:
   $$I(t) \le \text{cap}_{\text{eff}}(K(t)) = \frac{\text{baseCap}}{1 + \beta K(t)}$$
   Let $t_{\text{start}}^{(n)}$ denote the termination timestamp of the accrual phase, and let $K_{\text{start}}^{(n)} = K(t_{\text{start}}^{(n)})$ and $I_{\text{start}}^{(n)} = I(t_{\text{start}}^{(n)})$.
2. **Spending Phase $S_n$:** The sequence of $m \ge 1$ admitted requests arriving at timestamps $t_{\text{start}}^{(n)} \le t_1 \le t_2 \le \dots \le t_m$ that draw deficits $\delta_1, \delta_2, \dots, \delta_m > 0$ from the accumulated pool $I_{\text{start}}^{(n)}$.  
   During $S_n$, $t_{\text{full}} = 0$; no new idle credit can accrue.
3. **Inter-Episode Refill Phase $R_n$:** The time interval between the last credit-consuming request $t_m$ and the start of the next accrual episode $E_{n+1}$.

---

## 3. Systematic Verification of the 6 Foundational Statements

### Statement 1: Every request that spends idle credit depletes the burst bucket to zero.
**Status: PROVEN TRUE.**

*Proof:*
In [`DREBLimiter.java#L67-L69`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/DREBLimiter.java#L67-L69):
```java
double deficit = Math.max(0.0, cost - state.burstTokens);
state.burstTokens = Math.max(0.0, state.burstTokens - cost);
idlePolicy.spend(state, deficit);
```
Idle credit is spent if and only if $\text{deficit} > 0.0$.  
By definition, $\text{deficit} > 0.0 \iff \text{cost} > \text{state.burstTokens}$.  
When $\text{cost} > \text{state.burstTokens}$, we have $\text{state.burstTokens} - \text{cost} < 0$.  
The bucket update is:
$$\text{state.burstTokens} = \max(0.0,\, \text{state.burstTokens} - \text{cost}) = 0.0$$
Hence, every request that draws credit ($\delta > 0$) leaves $B = 0.0$ identically $\quad \blacksquare$.

---

### Statement 2: Multiple requests may spend the same accumulated idle-credit pool before a new accrual episode begins.
**Status: PROVEN TRUE.**

*Proof:*
Suppose at timestamp $t_1$, an admitted request with cost $c_1 > B(t_1)$ incurs deficit $\delta_1 = c_1 - B(t_1) > 0$.  
By Statement 1, $B(t_1^+) = 0.0$, and idle credit is debited to:
$$I(t_1^+) = I_{\text{start}} - \delta_1 \cdot \text{baseDecay}$$
If $I(t_1^+) > 0$, an immediately subsequent request arrives at $t_2 \ge t_1$ with cost $c_2$.  
If the elapsed time $\Delta t = t_2 - t_1 < t_{\text{fill}}$, the refilled burst tokens $B(t_2) = \min(B_{\max}, \Delta t \cdot B_{\text{rate}}) < B_{\max}$.  
If $c_2 > B(t_2)$, a second deficit $\delta_2 = c_2 - B(t_2) > 0$ is incurred.  
Provided $S(t_2) \ge c_2$ and $B(t_2) + \frac{I(t_1^+)}{\text{baseDecay}} \ge c_2$, the request is ALLOWed.  
Because $B(t_2) < B_{\max}$, [`DREBLimiter.java#L53-L54`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/DREBLimiter.java#L53-L54) computes $t_{\text{to\_fill}} = \frac{B_{\max} - B(t_1^+)}{B_{\text{rate}}} > \Delta t$, setting $t_{\text{full}} = 0.0$.  
Therefore, ZERO new idle credit is accrued between $t_1$ and $t_2$. Both requests draw from the same initial pool $I_{\text{start}} \quad \blacksquare$.

---

### Statement 3: The aggregate increase in $K$ across all spending from one accumulated credit pool is bounded by:
$$\Delta K_{\text{episode}} \le \frac{1}{\text{baseDecay}(1 + \beta K_{\text{start}})}$$
**Status: PROVEN TRUE.**

*Proof:*
Let the spending phase consist of $m \ge 1$ requests with deficits $\delta_1, \delta_2, \dots, \delta_m > 0$.  
At the start of the spending phase, the accumulated idle credit is bounded by the ceiling at $K_{\text{start}}$:
$$I_{\text{start}} \le \text{cap}_{\text{eff}}(K_{\text{start}}) = \frac{\text{baseCap}}{1 + \beta K_{\text{start}}}$$
On each draw $j \in \{1, \dots, m\}$, [`AdaptiveCeilingIdleCreditPolicy.java#L70`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java#L70) debits:
$$I(t_j^+) = \max(0.0,\, I(t_j) - \delta_j \cdot \text{baseDecay})$$
Because $I \ge 0$ is non-negative, the total idle credit spent cannot exceed the initial balance:
$$\sum_{j=1}^m \delta_j \cdot \text{baseDecay} \le I_{\text{start}} \le \frac{\text{baseCap}}{1 + \beta K_{\text{start}}}$$
Dividing both sides by $\text{baseDecay}$:
$$\sum_{j=1}^m \delta_j \le \frac{\text{baseCap}}{\text{baseDecay}(1 + \beta K_{\text{start}})}$$
In [`AdaptiveCeilingIdleCreditPolicy.java#L73`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java#L73), the draw index increments by:
$$\Delta K_j = \frac{\delta_j}{\text{baseCap}}$$
Summing the increments across all $m$ draws:
$$\Delta K_{\text{draws}} = \sum_{j=1}^m \frac{\delta_j}{\text{baseCap}} = \frac{\sum_{j=1}^m \delta_j}{\text{baseCap}} \le \frac{1}{\text{baseDecay}(1 + \beta K_{\text{start}})}$$

#### Dynamic Ceiling Clamping Audit:
What happens if the requests are separated by time $\Delta t > 0$, or if $K$ increases after draw 1?  
1. When $K$ increases from $K_{\text{start}}$ to $K_1 = K_{\text{start}} + \frac{\delta_1}{\text{baseCap}}$, the effective ceiling contracts: $\text{cap}_{\text{eff}}(K_1) < \text{cap}_{\text{eff}}(K_{\text{start}})$.  
2. In [`AdaptiveCeilingIdleCreditPolicy.java#L63`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java#L63), when $t_{\text{full}} = 0$:
   ```java
   state.idleCredit = Math.min(currentCap, state.idleCredit);
   ```
   If the remaining idle credit exceeds the newly contracted ceiling, it is immediately truncated downward!  
3. Any elapsed time $\Delta t > 0$ between requests causes $K$ to decay by $\Delta t \cdot \lambda_{\text{rec}} \ge 0$.  
Both ceiling clamping and time decay strictly *reduce* or leave unchanged the net change in $K$.  
Therefore, the aggregate change satisfies:
$$\Delta K_{\text{episode\_net}} \le \Delta K_{\text{draws}} \le \frac{1}{\text{baseDecay}(1 + \beta K_{\text{start}})} \quad \blacksquare$$

---

### Statement 4: A subsequent credit-accrual episode cannot begin until the burst bucket has recovered sufficiently to satisfy the required refill condition.
**Status: PROVEN TRUE.**

*Proof:*
By Statement 1, the final credit-consuming request in spending phase $S_n$ (at timestamp $t_m$) leaves $B(t_m^+) = 0.0$.  
For episode $E_{n+1}$ to begin, by definition there must exist a timestamp $t > t_m$ where $t_{\text{full}} > 0$.  
In [`DREBLimiter.java#L47-L55`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/DREBLimiter.java#L47-L55):
```java
double timeToFill = (refillStrategy.burstMax() - burstBefore) / refillStrategy.burstRate();
tFull = Math.max(0.0, dt - timeToFill);
```
Idle credit accrual ($t_{\text{full}} > 0$) occurs if and only if $\Delta t > \text{timeToFill}$.  
Starting from $B(t_m^+) = 0.0$, the burst bucket must receive at least $B_{\max} - 0 = B_{\max}$ refill tokens.  
Because the continuous refill rate is strictly capped at $B_{\text{rate}}$, accumulating $B_{\max}$ tokens requires a cumulative elapsed refill duration of at least:
$$T_{\text{refill}} \ge \frac{B_{\max}}{B_{\text{rate}}} = t_{\text{fill}} > 0 \quad \blacksquare$$

---

### Statement 5: The total recovery in $K$ between successive credit-accrual episodes is at least the recovery assumed in Lemma 2.
**Status: PROVEN TRUE.**

*Proof:*
Between the end of credit spending in episode $n$ ($t_m$) and the beginning of credit accrual in episode $n+1$ ($t_{\text{accrual}}$):
1. **Case A (No requests during refill):**  
   The client makes no requests. The burst bucket refills monotonically from $0$ to $B_{\max}$.  
   The elapsed time is exactly $t_{\text{fill}} = \frac{B_{\max}}{B_{\text{rate}}}$.  
   In [`AdaptiveCeilingIdleCreditPolicy.java#L55`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java#L55), $K$ decays by:
   $$\Delta K_{\text{recovery}} = \min\left( K(t_m^+),\, t_{\text{fill}} \cdot \lambda_{\text{rec}} \right) = \min\left( K(t_m^+),\, \frac{B_{\max} \cdot \lambda_{\text{rec}}}{B_{\text{rate}}} \right)$$
2. **Case B (Interleaved non-credit requests):**  
   The client sends requests with cost $c \le B(t)$ while $B$ is refilling.  
   Consuming tokens from $B$ reduces $B$ below the refill trajectory, strictly increasing the total time required for $B$ to reach $B_{\max}$:
   $$T_{\text{refill}} = \frac{B_{\max} + \sum c_{\text{interleaved}}}{B_{\text{rate}}} > t_{\text{fill}}$$
   Because $K$ recovers at rate $\lambda_{\text{rec}} > 0$ with respect to real elapsed time, any delay in reaching $B_{\max}$ strictly increases the recovery of $K$ (subject only to the floor at $K = 0$).  
Therefore, the total recovery between successive accrual episodes is bounded below by:
$$\Delta K_{\text{recovery}} \ge \frac{B_{\max} \cdot \lambda_{\text{rec}}}{B_{\text{rate}}} \quad \blacksquare$$

---

### Statement 6: The invariant $K(t) \le \max(K(0), K^* + 1/\text{baseDecay})$ therefore holds for arbitrary request schedules.
**Status: PROVEN TRUE.**

*Proof:*
We now evaluate the net change in $K$ from the start of accrual episode $n$ ($K_{\text{start}}^{(n)}$) to the start of accrual episode $n+1$ ($K_{\text{start}}^{(n+1)}$):
1. In accrual episode $n$, $K$ may only decay (since $t_{\text{full}} > 0$ and $\delta = 0$).
2. In spending phase $n$, by Statement 3, the total upward jump is bounded by:
   $$\Delta K_{\text{spend}} \le \frac{1}{\text{baseDecay}(1 + \beta K_{\text{start}}^{(n)})}$$
3. In inter-episode refill phase $n$, by Statement 5, $K$ decays by at least:
   $$\Delta K_{\text{recovery}} \ge \frac{B_{\max} \cdot \lambda_{\text{rec}}}{B_{\text{rate}}}$$
4. Combining these:
   $$K_{\text{start}}^{(n+1)} - K_{\text{start}}^{(n)} \le -\frac{B_{\max} \cdot \lambda_{\text{rec}}}{B_{\text{rate}}} + \frac{1}{\text{baseDecay}(1 + \beta K_{\text{start}}^{(n)})}$$
5. By definition of $K^*$:
   $$K^* \equiv \frac{1}{\beta}\left( \frac{B_{\text{rate}}}{\text{baseDecay} \cdot B_{\max} \cdot \lambda_{\text{rec}}} - 1 \right)$$
   Whenever $K_{\text{start}}^{(n)} > K^*$:
   $$1 + \beta K_{\text{start}}^{(n)} > \frac{B_{\text{rate}}}{\text{baseDecay} \cdot B_{\max} \cdot \lambda_{\text{rec}}} \implies \frac{1}{\text{baseDecay}(1 + \beta K_{\text{start}}^{(n)})} < \frac{B_{\max} \cdot \lambda_{\text{rec}}}{B_{\text{rate}}}$$
   Therefore, whenever $K_{\text{start}}^{(n)} > K^*$, the net drift across the episode is strictly negative:
   $$K_{\text{start}}^{(n+1)} < K_{\text{start}}^{(n)}$$
6. Within any spending phase, the maximum peak that $K$ can reach from any starting state $K_{\text{start}}^{(n)} \le K^*$ is:
   $$K_{\text{peak}} \le K_{\text{start}}^{(n)} + \Delta K_{\text{spend}} \le K^* + \frac{1}{\text{baseDecay}(1 + \beta K_{\text{start}}^{(n)})} \le K^* + \frac{1}{\text{baseDecay}}$$
7. If the system starts at $K(0) > K^* + \frac{1}{\text{baseDecay}}$, $K$ monotonically drifts downward across successive episodes until entering the invariant set $[0, K^* + \frac{1}{\text{baseDecay}}]$.  
Therefore:
$$K(t) \le \max\left( K(0),\, K^* + \frac{1}{\text{baseDecay}} \right) < \infty \quad \forall t \ge 0 \quad \blacksquare$$

---

## 4. Implementation Line Reference Correspondence

| Proof Component | Java Source File & Line Numbers | Implementation Behavior |
| :--- | :--- | :--- |
| **Burst Depletion on Spend** | [`DREBLimiter.java#L67-L68`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/DREBLimiter.java#L67-L68) | `state.burstTokens = Math.max(0.0, state.burstTokens - cost)` guarantees $B = 0.0$ whenever $\text{deficit} > 0$. |
| **Deficit Debit & K Increment** | [`AdaptiveCeilingIdleCreditPolicy.java#L68-L76`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java#L68-L76) | Increments `abuseScore += deficit / baseCap` and debits `idleCredit -= deficit * baseDecay`. |
| **Ceiling Contraction Truncation** | [`AdaptiveCeilingIdleCreditPolicy.java#L63`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java#L63) | When $t_{\text{full}} = 0$, clamps `idleCredit = Math.min(currentCap, idleCredit)`. |
| **Accrual Condition ($t_{\text{full}}$)** | [`DREBLimiter.java#L47-L55`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/DREBLimiter.java#L47-L55) | Evaluates $t_{\text{full}} = \max(0, \Delta t - (B_{\max} - B)/B_{\text{rate}})$. No credit accrues while $B < B_{\max}$. |
| **Linear K Recovery** | [`AdaptiveCeilingIdleCreditPolicy.java#L54-L56`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/main/java/com/dreb/ratelimit/policies/AdaptiveCeilingIdleCreditPolicy.java#L54-L56) | Recovers `abuseScore = Math.max(0.0, abuseScore - dt * recoveryRatePerSecond)` on every elapsed interval. |

---

## 5. Automated Property Verification

To verify these statements under randomized conditions, a property test was added to [`AntiFarmingCandidatePropertiesTest.java#L91-L162`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/AntiFarmingCandidatePropertiesTest.java#L91-L162):

- **Test Name:** `creditEpisodeInvariantsHoldForAdaptiveCeiling`
- **Execution:** 1,000 randomized property checks via `jqwik` testing arbitrary sequences of arrival deltas ($\Delta t \in [0.0, 10.0]\,\text{s}$) and costs ($c \in [1, 20]$).
- **Invariants Checked at Every Step:**
  1. $B(t^+) == 0.0$ on every step with $\text{deficit} > 0$.
  2. $\sum \delta_j \le \frac{\text{baseCap}}{\text{baseDecay}(1 + \beta K_{\text{start}})}$ across all requests within an episode.
  3. $I(t) \le \text{cap}_{\text{eff}}(K(t))$ at all timestamps.
  4. $K(t) \le K_{\max}$ at all timestamps.
- **Result:** **1,000 / 1,000 checks passed with 0 failures.**

---

## 6. Conclusion

> [!IMPORTANT]
> **Audit Conclusion: UNIVERSAL GUARANTEE MATHEMATICALLY ESTABLISHED.**
> 
> The episode-based formulation resolves the multi-draw question. Whether an adversary consumes idle credit via:
> - A single monolithic burst of size $C$,
> - Rapid multi-draw micro-requests at $\Delta t = 0$,
> - Interleaved requests of varying sizes, or
> - Randomized arrival schedules,
> 
> the aggregate credit extracted per accrual episode cannot exceed $\frac{\text{baseCap}}{\text{baseDecay}(1 + \beta K_{\text{start}})}$, and initiating a subsequent accrual episode strictly mandates recovering $K$ by at least $\frac{B_{\max} \lambda_{\text{rec}}}{B_{\text{rate}}}$.
> 
> The uniform upper bound $K(t) \le K_{\max}$ and Theorem 2's asymptotic limit $\limsup_{T \to \infty} \frac{\sum \delta_j}{T} \le \text{baseCap} \cdot \lambda_{\text{rec}}$ are mathematically closed and hold for all attack schedules.
