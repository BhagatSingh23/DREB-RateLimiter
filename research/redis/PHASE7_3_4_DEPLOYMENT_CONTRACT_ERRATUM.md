# PHASE 7.3.4: DEPLOYMENT CONTRACT ERRATUM AND EVIDENCE DISCLOSURES

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3.4 Tasks 2, 3, & 4  
**Repository Branch:** `research/systematic-improvement`  
**Starting Commit:** `e27b00c`  

---

## 1. Executive Summary

This erratum document establishes strict boundaries, disclosures, and precise wording for all operational deployment claims and experimental evidence across the project.

It resolves four specific areas:
1. **$G_{1,\delta}$ Physical-Time Deployment Scope:** Wording updated to prevent false claims of failover rate containment.
2. **Idempotency Gate H Five-Condition Specification:** Reconciled to include all five operational prerequisites.
3. **Replication Semantics of `WAIT 1`:** Decoupled from consensus, promotion determinism, and disk persistence.
4. **Evidence Classifications:** Decoupling unit/regression test execution from benchmark performance, separating latency measurement scopes, and scoping the 2,000-token bound per client key.

---

## 2. Erratum 1: Physical-Time $G_{1,\delta}$ Deployment Wording

> [!CAUTION]
> **Strict Physical-Time Boundary Disclosures:**
> 1. **Conditional Mathematical Envelope:** $G_{1,\delta}(T) = 1295.7143 + 1000.0 \cdot T\,\text{tokens}$ is valid **if and only if** all host clocks maintain continuous absolute UTC offset $|\tau - t| \le 5.0\,\text{ms}$ via slew-only discipline (`makestep 0 0`).
> 2. **Not Enforced in the Testbed:** The reported testbed executed inside containerized instances sharing a single host LinuxKit VM kernel. Independent multi-host clock discipline was **not enforced or measured**.
> 3. **Failover Reality:** Standard Redis Cluster failover does **not** automatically preserve $G_{1,\delta}$. In the absence of infrastructure-enforced slew synchronization, cross-node failover introduces unconstrained clock step jumps.
> 4. **No Bound Established Without Clock Assumption:** If the $\pm 5\,\text{ms}$ clock assumption is violated, **no finite physical-time rate envelope is established across failover transitions** by this research.

---

## 3. Erratum 2: Idempotency (Gate H) Five-Condition Contract

To eliminate any implication of unconditional exactly-once execution or failover immunity, Gate H is formally specified by five mandatory conditions:

$$\text{At-Most-Once Debit} \iff \begin{cases}
1. & \textbf{Same Request Identifier: } \text{Retry presents identical } \texttt{requestId} \\
2. & \textbf{Same Payload Fingerprint: } \text{SHA-256}(\text{payload}) \text{ matches original transaction} \\
3. & \textbf{Bounded Retention Window: } \text{Retry arrives at } \Delta t_{\text{retry}} < \tau_{\text{idemp}} = 60.0\,\text{seconds} \\
4. & \textbf{Replication Ack Prior to Crash: } \text{Primary confirms write via } \texttt{WAIT 1} \ge 1 \text{ before failing} \\
5. & \textbf{Record Visibility on Node Serving Retry: } \text{Acknowledged replica promotes and exposes record}
\end{cases}$$

### Precise Characterization of `WAIT 1`
- `WAIT 1 1000` is **strictly an in-memory replica acknowledgement condition**.
- It confirms that at least one replica received and processed the write stream into volatile RAM.
- It is **NOT** a distributed consensus protocol (such as Raft or Paxos).
- It does **NOT** guarantee replica disk durability (asynchronous `appendfsync everysec` leaves an unwritten window).
- In clusters with $N \ge 2$ replicas per primary, it does **NOT** guarantee that the acknowledging replica will win the election.

---

## 4. Erratum 3: Evidence Classification and Granularity Disclosures

### 4.1 Regression Tests vs. Benchmark Claims
- Passing the 43 tests in `src/test/java/com/dreb/ratelimit/redis` confirms **functional and algorithmic correctness** under simulated faults.
- It does **not** substitute for physical multi-server hardware benchmarking under sustained saturation load.

### 4.2 Latency Scope Decoupling
Reported latencies across the project must always be explicitly tagged with their measurement domain:
- **In-Memory Java Dispatch (Phase 5/5.4.1):** P50 = $42\,\text{ns}$, P99 = $83\,\text{ns}$ (Pure JVM nanosecond execution).
- **Lua Engine Execution (Phase 7):** P50 = $0.082\,\text{ms}$, P99 = $0.310\,\text{ms}$ (Redis internal script evaluation).
- **Async End-to-End Client Network (Phase 7):** P50 = $0.259\,\text{ms}$, P99 = $0.667\,\text{ms}$ (Jedis client round-trip over TCP).
- **Sync `WAIT 1` End-to-End Client Network (Phase 7.2):** P50 = $0.348\,\text{ms}$, P99 = $1.301\,\text{ms}$ (Jedis round-trip including replica ack).

### 4.3 Pareto Frontier Claims
All Pareto optimality claims refer strictly to:
- The empirical comparison datasets generated in Phase 1.1, Phase 5.3, and Phase 5.4.1.
- Evaluated against the six explicitly stated objective dimensions (burst FRR, post-attack FRR, farming suppression, sustained rate ceiling, nanosecond latency, and memory footprint).

### 4.4 Per-Key Granularity of the 2,000-Token Bound
- The bound $A_{\text{event\_burst}} \le 2 \times C_{\max} = 2000.0\,\text{tokens}$ applies **strictly to a single client key** (`{dreb:state:<clientId>}`) across exactly **one isolated discontinuity event**.
- The cumulative bound $A_{\text{total}}(T) \le 2000.0 + 1000.0 \cdot T\,\text{tokens}$ is **not an aggregate cluster bound**.
- For an adversary controlling $M$ independent client identities, aggregate admissions across a failover can reach $M \times 2000.0\,\text{tokens}$.
