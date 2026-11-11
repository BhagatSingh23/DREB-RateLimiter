# PHASE 7.3.2: G1 AND G1δ RATE ENVELOPE AND EXCESS ARITHMETIC RECONCILIATION

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.2 Task A  
**Repository Branch:** `research/systematic-improvement`  
**Starting Commit:** `5578582`  

---

## 1. Executive Summary

In Phase 7.3 and Phase 7.3.1, rate envelope excess calculations across physical time and state discontinuities introduced ambiguities regarding which envelope was used as the reference baseline. Specifically, $+704.2857\,\text{tokens}$ was interchangeably cited as the excess at $T = 10\,\text{ms}$ and as the limit as $T \to 0$.

This document provides the formal reconciliation of the two rate envelopes, explicitly separating:
1. **Ideal Physical Rate Envelope ($G_1$):** Zero-allowance reference bound assuming perfect UTC time.
2. **Timing-Tolerant Rate Envelope ($G_{1,\delta}$):** Relaxed physical-time bound incorporating a 10 ms two-point clock offset allowance ($\delta = 0.010\,\text{s}$).

**Critical Scientific Clarification:**  
$G_{1,\delta}$ is a **relaxed physical-time envelope**, *not* strict preservation of ideal $G_1$. The two envelopes are mathematically distinct and must never be conflated.

---

## 2. Formal Mathematical Definitions of the Envelopes

Let continuous physical time elapsed over an observation window be $T \ge 0$ (in seconds).  
For DREB-RCA70, nominal sustained refill rate is $R = 1000.0\,\text{tokens/s}$, and saturated burst capacity is $S_{\max} = 1285.7143\,\text{tokens}$.

### 2.1 Ideal Physical Rate Envelope: $G_1(T)$
Under an idealized continuous physical clock with zero drift and zero reading error ($\Delta \tau = T$):

$$G_1(T) = S_{\max} + R \cdot T = 1285.7143 + 1000.0 \cdot T\,\text{tokens}$$

- At $T = 0$: $G_1(0) = 1285.7143\,\text{tokens}$
- At $T = 10\,\text{ms} = 0.010\,\text{s}$: $G_1(0.010) = 1285.7143 + 10.0 = \mathbf{1295.7143\,\text{tokens}}$

### 2.2 Slew-Tolerant Physical Rate Envelope: $G_{1,\delta}(T)$
When host clocks possess an absolute UTC offset bound $|\tau - t| \le \epsilon = 5\,\text{ms}$, the maximum elapsed-time measurement error across two independent transactions (on the same host or during cross-node failover) is:

$$\delta = 2 \times \epsilon = 2 \times 0.005\,\text{s} = 0.010\,\text{s} = 10\,\text{ms}$$

Incorporating this elapsed-time allowance into the physical rate bound yields the relaxed envelope:

$$G_{1,\delta}(T) = S_{\max} + R \cdot (T + \delta) = S_{\max} + R \cdot \delta + R \cdot T$$
$$G_{1,\delta}(T) = 1285.7143 + (1000.0 \times 0.010) + 1000.0 \cdot T = \mathbf{1295.7143 + 1000.0 \cdot T\,\text{tokens}}$$

- At $T = 0$: $G_{1,\delta}(0) = \mathbf{1295.7143\,\text{tokens}}$
- At $T = 10\,\text{ms} = 0.010\,\text{s}$: $G_{1,\delta}(0.010) = 1295.7143 + 10.0 = \mathbf{1305.7143\,\text{tokens}}$

---

## 3. Reconciled Excess Arithmetic Across a Single State Reset ($A = 2000.0\,\text{tokens}$)

Consider a single state reset event (such as an un-replicated failover or state recreation) resulting in total event-attributable burst admissions of $A_{\text{event}} = 2 \times C_{\max} = 2000.0\,\text{tokens}$.

The excess above each envelope is computed as $\Delta(T) = A - \text{Envelope}(T)$:

| Evaluation Point | Reference Envelope | Allowable Limit (Tokens) | Single-Event Admission $A$ (Tokens) | Exact Excess $\Delta$ (Tokens) | Exact Fractional Excess |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **$T = 10\,\text{ms}$** | **Ideal $G_1(0.010)$** | $1295.7143$ | $2000.0$ | **$+704.2857$** | $+54.35\%$ |
| **$T = 10\,\text{ms}$** | **Relaxed $G_{1,\delta}(0.010)$** | $1305.7143$ | $2000.0$ | **$+694.2857$** | $+53.17\%$ |
| **Limit as $T \to 0$** | **Ideal $G_1(0)$** | $1285.7143$ | $2000.0$ | **$+714.2857$** | $+55.55\%$ |
| **Limit as $T \to 0$** | **Relaxed $G_{1,\delta}(0)$** | $1295.7143$ | $2000.0$ | **$+704.2857$** | $+54.35\%$ |

---

## 4. Key Arithmetic Reconciliations

### 4.1 Origin of the Coincidental Value "+704.2857 tokens"
The value **$+704.2857\,\text{tokens}$** occurs at two distinct mathematical points due to the identical numerical shift of $10.0\,\text{tokens}$ ($R \times 0.010\,\text{s}$):
1. **$T = 10\,\text{ms}$ relative to Ideal $G_1$:**
   $$\Delta_{G_1}(0.010) = 2000.0 - (1285.7143 + 10.0) = \mathbf{704.2857\,\text{tokens}}$$
2. **$T \to 0$ relative to Relaxed $G_{1,\delta}$:**
   $$\lim_{T \to 0} \Delta_{G_{1,\delta}}(T) = 2000.0 - (1295.7143 + 0.0) = \mathbf{704.2857\,\text{tokens}}$$

In Phase 7.3 and Phase 7.3.1, these two distinct cases were conflated. In Phase 7.3.2, every excess statement must explicitly identify both the elapsed time $T$ and the target envelope ($G_1$ vs $G_{1,\delta}$).

### 4.2 Non-Equivalence Statement
> [!WARNING]
> $G_1(T)$ and $G_{1,\delta}(T)$ are **not equivalent**.  
> $G_{1,\delta}(T)$ permits $+10.0\,\text{tokens}$ more admission at every point in time than $G_1(T)$.  
> Therefore, asserting adherence to $G_{1,\delta}$ constitutes an **admission of rate envelope relaxation**, not a preservation of ideal zero-allowance burst containment.
