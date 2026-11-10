# Phase 5.1 — Decision Latency Benchmark Audit & Methodological Critique

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`4c26bce`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Evaluated Code:** [`Phase5BaselineComparisonTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase5BaselineComparisonTest.java)  
**Verification Test:** [`Phase51CorrectiveAuditTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase51CorrectiveAuditTest.java)

---

## 1. Executive Summary & Defect Classification

In the Phase 5 report, decision latencies were reported as:
- **DREB-RCA70:** P50: $84.0\,\text{ns}$, P95: $250.0\,\text{ns}$, P99: $292.0\,\text{ns}$.
- **Token Bucket:** P50: $42.0\,\text{ns}$, P95: $209.0\,\text{ns}$, P99: $334.0\,\text{ns}$.
- **Throughput Claim:** $> 11.9\,\text{million ops/sec}$ single-thread capacity.

An independent audit of the benchmarking methodology in [`Phase5BaselineComparisonTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase5BaselineComparisonTest.java) reveals critical methodological limitations:
1. **Lack of JMH Harness:** The measurements were collected using a custom loop measuring `System.nanoTime()` across $50,000$ iterations rather than the Java Microbenchmark Harness (JMH).
2. **Apple Silicon Hardware Clock Quantization:** On macOS arm64, `System.nanoTime()` is tied to the 24 MHz Mach absolute time counter. The minimum discrete clock step is $\frac{10^9}{24,000,000} = \mathbf{41.6667\,\text{ns}}$. Consequently, every recorded latency is quantized to discrete multiples of $41.67\,\text{ns}$ ($41.7\,\text{ns}, 83.3\,\text{ns}, 125.0\,\text{ns}, 166.7\,\text{ns}, 208.3\,\text{ns}, 250.0\,\text{ns}, 291.7\,\text{ns}$).
3. **Timer Call Overhead Intrusion:** Invoking `System.nanoTime()` twice per decision adds $\approx 15 - 25\,\text{ns}$ of timer call overhead, which constitutes up to $30 - 50\%$ of the measured execution duration.
4. **Flawed Throughput Extrapolation:** Inferring sustained system throughput directly as the reciprocal of median in-memory latency ($\frac{1}{84\,\text{ns}} \approx 11.9\,\text{M ops/s}$) ignores multi-key cache eviction, concurrent hash-map contention, garbage collection pauses, and network transport overhead.

---

## 2. Analysis of the Custom Benchmark Loop

The benchmark loop in `Phase5BaselineComparisonTest.java` (lines 173–198) was structured as follows:
```java
// Measurement: 50,000 requests
int n = 50_000;
long[] samples = new long[n];
for (int i = 0; i < n; i++) {
    clock.set(30.0 + i * 0.001);
    long start = System.nanoTime();
    harness.limiter().allow("bench", 1);
    samples[i] = System.nanoTime() - start;
}
```

### 2.1 Critical Vulnerabilities in this Design
1. **Single Client Identifier Bias:** All $50,000$ calls query the exact same client ID (`"bench"`). This ensures $100\%$ L1/L2 cache residency for the `ClientState` object and zero ConcurrentHashMap hash-bucket contention.
2. **Absence of JIT Compiler Tiered Compilation Control:** Without JMH forks (`@Fork`), JIT compilation flags (C1 vs C2 / Tier 4) cannot be isolated across runs.
3. **Measurement Jitter:** Operating system context switches and scheduler ticks can inject outliers into the tail percentiles (P95, P99) that reflect OS thread interruptions rather than limiter execution latency.

---

## 3. Clock Quantization and Hardware Mechanics

On Apple Silicon (M-series processors running macOS Darwin 24.3.0), the ARM generic virtual timer frequency is fixed at $24\,\text{MHz}$:
$$\Delta t_{\text{tick}} = \frac{1}{24\,\text{MHz}} = 41.66667\,\text{ns}$$

When `System.nanoTime()` is queried before and after `allow()`, the returned difference must be an integer multiple of ticks:

| Timer Tick Multiplier ($k$) | Nominal Quantized Duration | Empirical Percentile in Phase 5 Data |
| :--- | :--- | :--- |
| $1 \times \text{tick}$ | $41.67\,\text{ns}$ | $42.0\,\text{ns}$ (Token Bucket P50, DREB-AC P99) |
| $2 \times \text{ticks}$ | $83.33\,\text{ns}$ | $83.0\,\text{ns} - 84.0\,\text{ns}$ (RCA70 P50, Static DB P99) |
| $3 \times \text{ticks}$ | $125.00\,\text{ns}$ | $125.0\,\text{ns}$ (DREB v2 P99, TB P99) |
| $4 \times \text{ticks}$ | $166.67\,\text{ns}$ | $167.0\,\text{ns}$ (DREB-AC P95) |
| $5 \times \text{ticks}$ | $208.33\,\text{ns}$ | $208.0\,\text{ns} - 209.0\,\text{ns}$ (Static DB P95, DREB v2 P95) |
| $6 \times \text{ticks}$ | $250.00\,\text{ns}$ | $250.0\,\text{ns}$ (RCA70 P95, DREB-AC P99) |
| $7 \times \text{ticks}$ | $291.67\,\text{ns}$ | $292.0\,\text{ns}$ (RCA70 P99) |
| $8 \times \text{ticks}$ | $333.33\,\text{ns}$ | $334.0\,\text{ns}$ (Token Bucket P99) |

This demonstrates conclusively that the variations between $84\,\text{ns}$ and $292\,\text{ns}$ are primarily discrete jumps between $2$ and $7$ hardware timer ticks, obscured by clock quantization!

---

## 4. Methodological Reclassification

To preserve scientific integrity:

1. **Reclassify Phase 5 Latency Data:**
   The numbers reported in Phase 5 must be formally classified as **preliminary, quantized microbenchmark observations** rather than definitive production latency profiles.
2. **Audited Performance Characterization:**
   - In single-threaded in-memory execution, all five policies execute between **$1$ and $7$ hardware timer ticks** ($\approx 40 - 290\,\text{ns}$).
   - DREB-RCA70 requires approximately $1 - 2$ additional ticks ($\approx 40 - 80\,\text{ns}$) compared to Token Bucket due to its dual-bucket refill calculation, exponential decay recovery, and adaptive ceiling evaluation.
   - In any production API gateway, where network transport latency is measured in milliseconds ($1 - 10\,\text{ms}$) and Redis network roundtrips require $200 - 800\,\mu\text{s}$, a sub-microsecond in-memory decision time ($< 0.3\,\mu\text{s}$) represents **less than $0.1\%$ of total request latency**.
3. **Throughput Claim Correction:**
   The claim of "$11.9\,\text{M ops/sec}$" is withdrawn. Production throughput must be evaluated under multi-threaded contention with realistic client key distributions.
