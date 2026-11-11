# PHASE 7.3.1: CHRONY CONFIGURATION AND CLOCK SYNCHRONIZATION AUDIT

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.1 Workstream C  
**Repository Branch:** `research/systematic-improvement`  
**Starting Reference Commit:** `c4a857cd371dfb984a44ffee0684176303c84477`  

---

## 1. Executive Summary

In Phase 7.3, the preservation of the rate limit envelope under distributed Redis execution was predicated on clock monotonicity and a bounded offset ($|\tau - t| \le 5\,\text{ms}$) maintained via Chrony NTP synchronization configured in slew-only mode.

This audit independently inspects the runtime environment of the Phase 7 testbed and evaluates the technical validity of that assumption.

**Key Findings:**
1. **Absence of NTP Daemon:** Neither the `dreb-redis-cluster` container nor the standalone `lb-adaptive-redis-1` container has `chronyd` or any NTP daemon installed.
2. **Container Time Inheritance:** In Docker environments (particularly Docker Desktop on macOS using LinuxKit), containers share the host VM's kernel clock. Containers lack `CAP_SYS_TIME` by default and cannot independently discipline or slew the system clock.
3. **Chrony Configuration Semantics:** The `makestep` directive in Chrony (`makestep <threshold> <limit>`) steps the clock if the offset exceeds `<threshold>` during the first `<limit>` updates. Slew-only operation requires omitting `makestep` entirely or setting `makestep 0 0`.
4. **Classification:** Slew-only synchronization is classified as **`[ASSUMED_NOT_ENFORCED]`** in the current experimental testbed. It is an operational prerequisite of the deployment model, not an active infrastructure guarantee verified by the testbed.

---

## 2. Empirical Verification of Testbed Containers

To evaluate whether Chrony is active in the testbed, we executed system inspection commands inside the running containers:

```bash
docker exec dreb-redis-cluster which chronyd
docker exec lb-adaptive-redis-1 which chronyd
```

**Results:**
- `dreb-redis-cluster`: `chronyd not found` (Exit code 1)
- `lb-adaptive-redis-1`: `chronyd not found` (Exit code 1)

Checking installed packages and active processes:
```bash
docker exec dreb-redis-cluster ps aux
```
The process table contains only `redis-server` instances and shell supervisor scripts. No time-synchronization daemon (`chronyd`, `ntpd`, `systemd-timesyncd`) is executing inside any container.

### OS and Kernel Context
- **Base OS:** Debian GNU/Linux 12 (bookworm)
- **Kernel:** Linux 6.12.76-linuxkit #1 SMP aarch64
- **Runtime:** Docker Desktop on macOS (Apple Silicon Darwin)

---

## 3. Containerized Clock Architecture and Limitations

Containers do not have private hardware clocks. In Linux:
1. `CLOCK_REALTIME` and `CLOCK_MONOTONIC` are managed by the host kernel (or the LinuxKit VM kernel in Docker Desktop).
2. All containers running on the same Linux host / VM share the exact same kernel timebase.
3. To adjust or slew the system clock (`adjtimex(2)`, `settimeofday(2)`), a process requires the Linux capability `CAP_SYS_TIME`. Standard Docker containers run without this capability for security isolation.
4. Consequently, clock synchronization cannot be enforced inside individual Redis containers; it must be enforced at the host/hypervisor layer.

In our local testbed, time consistency across the 6 Redis cluster nodes was maintained not by Chrony disciplining independent physical oscillators, but because **all 6 Redis instances run on the same shared VM kernel clock**. Hence, relative drift between nodes on the local machine was identically zero by virtue of colocation.

---

## 4. Chrony Configuration Semantics: Slew vs. Step

When deploying DREB-RCA70 to production environments where Redis nodes reside on independent physical or virtual hosts, clock synchronization must be configured correctly.

### 4.1 Chrony `makestep` Directive Syntax
In `chrony.conf`:
```
makestep <threshold> <limit>
```
- `<threshold>`: Offset magnitude (in seconds) beyond which Chrony will step (discontinuously jump) the clock rather than slew it.
- `<limit>`: Number of clock updates after startup during which stepping is permitted. If set to a negative number, stepping is permitted indefinitely.

**Standard Default Configuration (Dangerous for Rate Limiters):**
```
makestep 1.0 3
```
*Risk:* If the clock drifts by more than 1 second during the first 3 updates, Chrony steps the clock. If stepped forward, rate limits prematurely refill (burst leakage). If stepped backward, elapsed time becomes negative or zero, stalling token accrual.

### 4.2 Slew-Only Configuration Required by DREB-RCA70
To guarantee clock monotonicity and prevent step discontinuities, the deployment configuration must enforce slew-only mode:

```
# Slew-only mode: never step the clock
# Omitting 'makestep' entirely or explicitly setting:
makestep 0 0

# Limit max slew rate if necessary (default max slew rate is 83333 ppm / ~8.3%)
maxslewrate 500
```
Under this configuration, Chrony uses the Linux `adjtimex()` kernel facility to gradually adjust the clock frequency until the offset is eliminated, ensuring:
1. First derivative of reported time is strictly positive: $\frac{d\tau}{dt} > 0$ (monotonicity).
2. No instantaneous jumps occur.
3. The absolute offset is continuously driven toward zero.

---

## 5. Audit Classification and Deployment Requirements

| Requirement | Testbed Status | Production Classification | Justification |
| :--- | :---: | :---: | :--- |
| **Chrony Slew-Only (`makestep 0 0`)** | Absent (Shared VM Kernel) | **`[ASSUMED_NOT_ENFORCED]`** | The testbed lacks `chronyd`. Slew synchronization is an architectural prerequisite assumed by the proof, not enforced by the container setup. |
| **Offset Bound ($|\tau - t| \le 5\,\text{ms}$)** | Colocated ($\Delta \approx 0$) | **`[ASSUMED_NOT_ENFORCED]`** | Multi-node drift was trivial due to shared kernel, not active NTP disciplining across distinct network hosts. |
| **Monotonic Time Progression** | Enforced by Host Kernel | **`[CONFIGURATION_ENFORCED]`** | The LinuxKit VM kernel provides monotonic progression in the absence of external step calls. |

### Production Deployment Prerequisite Checklist
For the physical-time rate bound $G_{1,\delta}$ to hold in production:
1. [ ] Every Redis host must run `chronyd` at the host OS level (outside container isolation).
2. [ ] Configuration must specify `makestep 0 0` (or completely omit `makestep`) to forbid backward and forward clock stepping.
3. [ ] At least 3 independent NTP/PTP server sources must be configured with peer polling intervals $\le 16\,\text{s}$ (`poll 4`).
4. [ ] An alert daemon must monitor `chronyc tracking` and page on `Last offset` exceeding $\pm 5\,\text{ms}$.
