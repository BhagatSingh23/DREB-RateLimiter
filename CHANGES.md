# DREB Rate Limiter: Changelog and Behavioral Differences

This document records all behavioral and mathematical changes introduced in `v1.0.0-phase1` relative to the prototype draft evaluated in the initial paper revision. For each change, the expected direction of effect on measured acceptance rates is documented below to explain deviations from superseded results.

---

### 1. P1: Mid-Gap Burst-Bucket Idle-Credit Accrual Correction

- **Component:** `DREBLimiter.java`, `DREBLimiterV2.java`, `LinearIdleCreditPolicy.java`, `AdaptiveIdleCreditPolicy.java`, `RedisStateStore.java` (Lua script).
- **Previous behavior:** Burst bucket fullness was checked *after* refilling. If the bucket was full at the end of interval $\Delta t$, idle credit accrued as $\Delta t \cdot \text{idle\_gain}$ over the entire duration, even if the bucket had been empty or filling for most of that interval.
- **Corrected behavior:** Idle credit accrues strictly for the sub-interval during which the burst bucket was actually full:
  $$t_{\text{full}} = \begin{cases} \Delta t & \text{if } b_0 \ge B_{\max} \\ \max\left(0, \Delta t - \frac{B_{\max} - b_0}{B_{\text{rate}}}\right) & \text{if } B_{\text{rate}} > 0 \\ 0 & \text{otherwise} \end{cases}$$
- **Direction of effect on acceptance rates:** **DECREASES (or neutral)**. Workloads with frequent pauses or bursts following partial drains will receive fewer spurious idle credits, slightly lowering acceptance rates compared to superseded benchmark runs.

---

### 2. P4: Large-Request and Variable-Cost Burst Admission Bounding

- **Component:** `DREBLimiter.checkAndConsume()`, `DREBLimiterV2.checkAndConsume()`, `RedisStateStore.java` (Lua script).
- **Previous behavior:** Admission checked `burstTokens + idleCredit >= cost` without scaling `idleCredit` by the debit penalty `idleDecay`. In the burst-ceiling-matched mapping, a single request of cost up to $\min(S_{\max}, B_{\max} + \text{idle\_cap}) = 9C/7$ was admitted from full state, exceeding the nominal ceiling $C$.
- **Corrected behavior:** Admission requires `burstTokens + (idleCredit / effectiveDecay) >= cost`. Single requests from full state can now never exceed the intended ceiling $C = B_{\max} + \text{idle\_cap}/\text{idleDecay}$.
- **Direction of effect on acceptance rates:** **DECREASES (or neutral)**. For unit-cost workloads ($c = 1$), there is zero mathematical difference. For variable-cost or batch-cost workloads ($c > B_{\max}$), large requests that previously over-consumed capacity are now correctly denied.

---

### 3. P7: Monotonic Clock Rollback Clamping

- **Component:** `AbstractRateLimiter.java`, `RedisStateStore.java` (Lua script).
- **Previous behavior:** When time moved backward ($now < \text{lastRefillTs}$), $\Delta t$ was clamped to 0, but $\text{lastRefillTs}$ was overwritten with the earlier timestamp ($now$). Subsequent forward advances re-refilled tokens across the rewound interval.
- **Corrected behavior:** $\text{lastRefillTs}$ is monotonically non-decreasing: $\text{lastRefillTs} = \max(\text{lastRefillTs}, now)$. Clock rollbacks neither grant refill nor regress the refill baseline.
- **Direction of effect on acceptance rates:** **DECREASES (or neutral)** in scenarios with non-monotonic timestamps or NTP adjustments, preventing illegitimate double refills.

---

### 4. P7: Input Validation (Non-Positive Cost and Non-Finite Timestamps)

- **Component:** `AbstractRateLimiter.allow()`.
- **Previous behavior:** Unchecked inputs allowed $cost \le 0$ (which could bypass limits or alter balances) and non-finite timestamps (causing NaN propagation).
- **Corrected behavior:** Explicitly throws `IllegalArgumentException` for $cost \le 0$ or non-finite clock timestamps (`Double.isNaN`, `Double.isInfinite`).
- **Direction of effect on acceptance rates:** Neutral on valid compliant traffic; turns invalid requests into fast-fail exceptions.

---

### 5. P7: Distributed Redis `abuseScore` Persistence and Server-Side Atomicity

- **Component:** `RedisStateStore.java`, `ClientState.java`.
- **Previous behavior:** `RedisStateStore` omitted `abuseScore` in its `HGETALL` and `HSET` operations, silently dropping abuse state on every request and forcing distributed DREB v2 to evaluate with $abuseScore = 0.0$. Operations were also non-atomic read-then-writes.
- **Corrected behavior:** `abuseScore` is persisted in Redis hashes (`abuse_score`). All load, refill, mid-gap $t_{\text{full}}$ calculation, idle accrual, abuse decay, cost scaling, and state persistence execute atomically inside a single Lua script.
- **Direction of effect on acceptance rates:** **DECREASES** acceptance rates for repeat-abuser workloads in multi-instance Redis deployments, as escalating abuse penalties are now actually enforced across distributed nodes.
