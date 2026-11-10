# Phase 3.1: Base Burst Allocation Ablation (RCA60 vs RCA70 vs RCA80)

## 1. Executive Summary

This ablation evaluates the foundational architectural parameter of DREB-RCA: the base burst allocation ratio $\alpha = B_{\max} / C$, comparing **RCA60** ($\alpha = 0.60$), **RCA70** ($\alpha = 0.70$), and **RCA80** ($\alpha = 0.80$) while holding nominal burst target $C = 1,000$ tokens, sustained rate $R = 1,000$ tokens/s, debit factor $\text{baseDecay} = 1.5$, ceiling sensitivity $\beta = 0.75$, and recovery rate $\lambda_{\text{rec}} = 0.05\text{ s}^{-1}$ constant.

### Key Findings:
- **Allocation governs the frontier between false drops and attack throughput:**
  Increasing $\alpha$ from $0.60 \to 0.70 \to 0.80$ steadily decreases compliant bursty False Rejection Rate (FRR drops from $20.02\% \to 17.65\% \to 16.33\%$).
- **The Total Attack Exposure Cost:**
  Higher base allocation linearly increases the volume of unconditional tokens an attacker can harvest without idle credit. Total admitted adversarial throughput increases from $0.4597R \to 0.5031R \to 0.5464R$ (+18.9% increase from RCA60 to RCA80).
- **Overload Under-Servicing is completely resolved for $\alpha \ge 0.70$:**
  At $1.50R$ overload, all three allocations achieve $\approx 1.003R$. However, at $1.25R$ overload, RCA60 experiences mild under-servicing ($0.8951R$), whereas RCA70 ($0.9903R$) and RCA80 ($0.9968R$) deliver full contract rate.
- **RCA70 represents the optimal operational knee:**
  It achieves full overload throughput, beats standard Token Bucket on bursty FRR, protects upstream servers by limiting total attack volume to $0.5031R$, and preserves a substantial 300-token ($30\%$) dynamic idle incentive.

---

## 2. Experimental Data Matrix

All results are averaged across 10 random seeds ($N=10$, seeds $4000-4009$) using the test harness [`Phase3AblationTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase3AblationTest.java):

| Metric / Evaluation Dimension | RCA60 ($\alpha = 0.60$) | **RCA70 ($\alpha = 0.70$)** | RCA80 ($\alpha = 0.80$) |
| :--- | :---: | :---: | :---: |
| **Base Capacity ($B_{\max}$)** | 600.0 tokens | **700.0 tokens** | 800.0 tokens |
| **Effective Idle Headroom ($I_{\text{eff}} = C - B_{\max}$)** | 400.0 tokens ($40\%$) | **300.0 tokens ($30\%$)** | 200.0 tokens ($20\%$) |
| **Raw Stored Cap ($\text{baseCap} = 1.5 \cdot I_{\text{eff}}$)** | 600.0 tokens | **450.0 tokens** | 300.0 tokens |
| **Bursty FRR at $0.783R$** $\downarrow$ | $20.02 \pm 2.45\%$ | **$17.65 \pm 2.48\%$** | $16.33 \pm 2.52\%$ |
| **Bursty FRR at $0.900R$** $\downarrow$ | $23.14 \pm 2.38\%$ | **$21.18 \pm 2.41\%$** | $20.08 \pm 2.43\%$ |
| **Overload Throughput at $1.25R$** ($\to 1.0$) | $0.8951 \pm 0.0000R$ | **$0.9903 \pm 0.0000R$** | $0.9968 \pm 0.0000R$ |
| **Overload Throughput at $1.50R$** ($\to 1.0$) | $1.0025 \pm 0.0000R$ | **$1.0031 \pm 0.0000R$** | $1.0031 \pm 0.0000R$ |
| **Total Adversarial Throughput ($1.00R$)** $\downarrow$ | **$0.4597 \pm 0.0000R$** | **$0.5031 \pm 0.0000R$** | $0.5464 \pm 0.0000R$ |
| **Credit-Funded Adversarial Throughput** $\downarrow$ | $0.0798 \pm 0.0000R$ | **$0.0598 \pm 0.0000R$** | **$0.0398 \pm 0.0000R$** |
| **Ordinary Refill Adversarial Throughput** | $0.3800 \pm 0.0000R$ | **$0.4433 \pm 0.0000R$** | $0.5067 \pm 0.0000R$ |
| **Post-Idle Burst Acceptance ($C = 1,000$)** | 100.0% | **100.0%** | 100.0% |

---

## 3. Detailed Mechanism Analysis

### 3.1 Why Bursty FRR Improves with $\alpha$
In bursty ON/OFF traffic, bursts arrive stochastically with mean size $\approx 600$ tokens.
- For RCA60 ($B_{\max} = 600$), any burst slightly above the mean ($>600$) immediately exhausts the primary bucket and requires idle credit. If the prior idle period was brief, the request is partially or fully dropped.
- For RCA70 ($B_{\max} = 700$), the primary bucket absorbs bursts up to 700 tokens unconditionally without needing any idle credit. This cuts the False Rejection Rate from $20.02\%$ to $17.65\%$.
- For RCA80 ($B_{\max} = 800$), the primary bucket absorbs even larger bursts unconditionally, further reducing FRR to $16.33\%$.

### 3.2 The Total Attack Penetration Trade-off
Why does RCA80 admit more total adversarial traffic despite drawing fewer credit tokens?
- When the adversary executes periodic bursts ($c = 1,000$ every $1.54\text{ s}$), it reaps two streams: ordinary bucket refill and idle-credit draws.
- In RCA80, $\text{baseCap}$ is smaller ($300$ vs $450$), so the adaptive ceiling clamps the credit-funded portion to $0.0398R$ (vs $0.0598R$ in RCA70).
- However, RCA80's primary bucket accumulates $800$ tokens every cycle (vs $700$ in RCA70). Over 38 bursts in 60s, this injects $30,400$ ordinary tokens ($0.5067R$), compared to $26,600$ ordinary tokens ($0.4433R$) in RCA70.
- **Net result:** RCA80 admits $0.5464R$ total traffic, exposing upstream application servers to $+8.6\%$ more attack volume than RCA70 ($0.5031R$) and $+18.9\%$ more than RCA60 ($0.4597R$).

### 3.3 Dynamic Incentive Degradation
DREB's core research premise is rewarding clients for demonstrated idleness.
- At $\alpha = 0.60$, idle headroom is $400$ tokens ($40\%$).
- At $\alpha = 0.70$, idle headroom is $300$ tokens ($30\%$).
- At $\alpha = 0.80$, idle headroom is $200$ tokens ($20\%$).
As $\alpha \to 1.0$, DREB degenerates into a static Token Bucket. $\alpha = 0.70$ preserves a substantial dynamic burst reward while completely eliminating the over-enforcement defect.

---

## 4. Conclusion

The base burst allocation parameter $\alpha$ is a **fundamental, necessary architectural mechanism**:
1. Setting $\alpha \le 0.50$ (as in DREB v1's $3/7 \approx 0.43$) triggers the conjunction trap, causing severe under-servicing under overload ($0.8465R$) and elevated FRR.
2. Setting $\alpha = 0.70$ successfully breaks the conjunction trap, eliminates overload under-servicing, reduces FRR below standard Token Bucket, and maintains superior upstream attack protection compared to $\alpha = 0.80$.
