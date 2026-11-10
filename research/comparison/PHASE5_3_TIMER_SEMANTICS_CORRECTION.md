# Phase 5.3 — Darwin Timer Semantics & Hardware Qualification Correction

**Repository:** DREB-RateLimiter  
**Branch:** `research/systematic-improvement`  
**Starting Commit:** [`a3a9e39`](file:///Users/cruise/Desktop/DREB-RateLimiter)  
**Host Architecture:** macOS Darwin 24.3.0, Apple Silicon (arm64, M-series)  
**Verification Test:** [`Phase53FinalVerificationTest.java`](file:///Users/cruise/Desktop/DREB-RateLimiter/src/test/java/com/dreb/ratelimit/Phase53FinalVerificationTest.java)

---

## 1. Executive Summary & Correction Statement

In Phase 5.2, the observed latency quantization in benchmark percentiles was attributed to an exact "24 MHz ARM Generic Timer physical hardware frequency" based on the Darwin `mach_timebase_info` return values (`numer=125, denom=3`).

An independent review of Darwin kernel architecture and authoritative Apple documentation identifies a crucial scientific distinction that must be qualified:

> **Formal Correction:**
> 1. **Verified OS Conversion Factor:** It is factually established that on this platform, the Darwin operating system API `mach_timebase_info` returns $\text{numer} = 125, \text{denom} = 3$, defining an OS timebase conversion factor of:
>    $$\Delta t_{\text{unit}} = \frac{125}{3} = \mathbf{41.666667\,\text{ns / Mach tick}}$$
> 2. **Retraction of Hardware Frequency Claim:** The mathematical reciprocal of this conversion factor ($10^9 / (125/3) = 24.0\,\text{MHz}$) cannot be equated with the physical hardware counter frequency (`CNTFRQ_EL0`) solely from user-space OS timebase ratios. Apple's technical documentation explicitly designates Mach absolute-time units as an **opaque platform timebase**, not a hardware cycle guarantee.
> 3. **Measurement Impact:** In user-space Java execution, `System.nanoTime()` invokes `mach_absolute_time()` and multiplies by this timebase ratio. Consequently, the **effective observable resolution** of in-memory latency benchmarks on this host is quantized to discrete multiples of $\approx 41.67\,\text{ns}$. This quantization is documented strictly as an **empirical measurement observation**, without asserting unverified physical crystal specifications.

---

## 2. Multi-Layer Timing Semantics Taxonomy

To ensure complete scientific rigor, the timing pipeline is categorized into five distinct layers:

| Timing Layer | Technical Definition | Verified State on Host System | Observability Status |
| :--- | :--- | :--- | :--- |
| **Layer 1: Physical Hardware Counter** | The hardware crystal oscillator and ARM register (`CNTVCT_EL0` / `CNTFRQ_EL0`). | Unverified from user-space (requires privileged EL1/kernel access). | **Retracted as unverified claim.** |
| **Layer 2: Mach Absolute-Time Unit** | The abstract integer tick returned by `mach_absolute_time()`. | Increments monotonically; opaque OS counter unit. | **Confirmed.** |
| **Layer 3: Nanosecond Conversion Ratio** | The ratio $\frac{\text{numer}}{\text{denom}}$ exposed by `mach_timebase_info`. | Exactly $\frac{125}{3} \approx 41.6667\,\text{ns / tick}$ on Darwin arm64. | **Confirmed via C ABI.** |
| **Layer 4: Java Runtime Timing API** | OpenJDK HotSpot's `os::javaTimeNanos()` implementing `System.nanoTime()`. | Queries `mach_absolute_time()` and scales by `numer/denom`. | **Confirmed in HotSpot source.** |
| **Layer 5: Benchmark Quantization** | The discrete clustering of microbenchmark latency samples. | Samples cluster at $42\,\text{ns}, 83\,\text{ns}, 125\,\text{ns}, 208\,\text{ns}, 292\,\text{ns}$. | **Confirmed empirical artifact.** |

---

## 3. Authoritative Apple Documentation on Mach Timebase

Apple's authoritative developer documentation (Technical Q&A QA1398 and `mach_time.h`) establishes the following invariants:
1. **Opaque Time Units:** Mach absolute-time units are defined as abstract ticks. The relationship between Mach ticks and seconds is system-dependent.
2. **Mandatory Conversion Contract:** Applications must never assume a fixed hardware frequency or assume that $1\,\text{tick} = 1\,\text{nanosecond}$. Instead, applications must call `mach_timebase_info(&info)` and calculate:
   $$\text{nanoseconds} = \frac{\text{ticks} \times \text{info.numer}}{\text{info.denom}}$$
3. **Architecture Independence:** While Intel-based Macs historically exposed $\text{numer}=1, \text{denom}=1$ (1 ns ticks), Apple Silicon exposed $\text{numer}=125, \text{denom}=3$. Treating this scaling ratio as proof of physical hardware oscillator properties conflates the kernel's virtual timebase abstraction with physical silicon layout.

---

## 4. Benchmark Quantization Mechanics in HotSpot

In OpenJDK HotSpot on macOS Darwin (`os_bsd.cpp`), `System.nanoTime()` is compiled as:
```cpp
jlong os::javaTimeNanos() {
    uint64_t ticks = mach_absolute_time();
    return (ticks * _timebase_info.numer) / _timebase_info.denom;
}
```
Because `ticks` is an integer:
- $\text{ticks} = 1 \implies \text{nanos} = \lfloor 125 / 3 \rfloor = 41\,\text{ns}$ (or rounded to $42\,\text{ns}$).
- $\text{ticks} = 2 \implies \text{nanos} = \lfloor 250 / 3 \rfloor = 83\,\text{ns}$.
- $\text{ticks} = 3 \implies \text{nanos} = \lfloor 375 / 3 \rfloor = 125\,\text{ns}$.
- $\text{ticks} = 5 \implies \text{nanos} = \lfloor 625 / 3 \rfloor = 208\,\text{ns}$.
- $\text{ticks} = 7 \implies \text{nanos} = \lfloor 875 / 3 \rfloor = 291\,\text{ns}$ (reported as $292\,\text{ns}$).

Any in-memory operation executing faster than 1 tick ($\approx 41.67\,\text{ns}$) will either measure $0\,\text{ns}$ (if it begins and ends within the same tick) or $41.67\,\text{ns}$ (if it crosses a tick boundary). This explains the discrete percentile staircases in Phase 5 without requiring unproven physical frequency assertions.

---

## 5. Corrective Text for Research Manuscript

All manuscript references to benchmarking latency must adopt the following phrasing:
> *"Decision latencies were profiled in Java 21 on macOS Darwin arm64 using `System.nanoTime()`. On this host environment, the operating system Mach timebase (`mach_timebase_info`) enforces an effective measurement quantum of approximately $41.67\,\text{ns}$ ($\text{numer}=125, \text{denom}=3$). Consequently, observed latency percentiles exhibit discrete clustering across tick multiples ($83\,\text{ns}$ to $292\,\text{ns}$) and represent preliminary microbenchmark observations."*
