# PHASE 7.3: FORMAL FAILURE MODEL RECONCILIATION

**Target System:** DREB-RCA70 Production Redis Adapter (`com.dreb.ratelimit.redis`)  
**Audit Phase:** Phase 7.3 Workstream B  
**Repository Branch:** `research/systematic-improvement`  
**Starting Reference Commit:** `8f55c0d4193bee5f9826f785eae9124feba2f602`  

---

## 1. Executive Summary

In Phase 7.2, the restricted failure model $\mathcal{M}_{\text{G1-Valid}}$ was proposed as the operating domain under which the G1 burst envelope could be preserved across node crashes. However, an independent scientific audit requires that every assumption underpinning a theoretical guarantee be categorized according to its enforcement mechanism.

We reject the practice of treating infrastructure assumptions as intrinsic mathematical properties of the software. This document formalizes the complete failure model for distributed Redis rate limiting and classifies all ten structural assumptions into:
- **`[ENFORCED_BY_IMPLEMENTATION]`**: Deterministically enforced by adapter code and Lua logic.
- **`[ENFORCED_BY_REDIS_CONFIG]`**: Enforced by Redis engine configuration parameters.
- **`[ENFORCED_BY_DEPLOYMENT_INFRA]`**: Enforced by external orchestration, network topology, or host OS.
- **`[ASSUMED_NOT_ENFORCED]`**: An un-fenced precondition; if violated by the environment, guarantees lapse.
- **`[EMPIRICALLY_OBSERVED_ONLY]`**: Exhibited during local test execution but not guaranteed by specification.

---

## 2. Rigorous 10-Point Assumption Classification Matrix

| Assumption Domain | Specific Operational Assumption | Enforcement Classification | Failure Boundary if Violated |
| :--- | :--- | :---: | :--- |
| **1. Connection Affinity** | `EVAL` and `WAIT 1` must execute on the exact same client TCP socket. | **`[ENFORCED_BY_IMPLEMENTATION]`** (when using Standalone single-checkout provider) / **`[ASSUMED_NOT_ENFORCED]`** (under standard `JedisCluster`) | `WAIT` tracks unlinked connection offset; write replication is completely unconfirmed. |
| **2. Replica Acknowledgement** | Replica receives write in memory and advances TCP offset prior to primary acknowledging client. | **`[ENFORCED_BY_REDIS_CONFIG]`** (`min-replicas-to-write 1` or synchronous `WAIT 1`) | Asynchronous primary crash permits subsequent duplicate burst (+704.29 tokens). |
| **3. Replica Disk Persistence** | Replicated state is committed to persistent storage (`fsync`) on the replica. | **`[ASSUMED_NOT_ENFORCED]`** (Standard Redis defaults to `appendfsync everysec`) | Power loss of primary + replica loses un-fsync'd debits, rolling back state. |
| **4. Promotion Eligibility** | Only in-sync replicas that received the debit are eligible for failover election. | **`[ENFORCED_BY_REDIS_CONFIG]`** (`cluster-replica-validity-factor 10`) | Stale replica promoted; debit history permanently lost. |
| **5. Highest-Offset Election** | Consensus election guarantees that the replica with the highest replication offset wins promotion. | **`[EMPIRICALLY_OBSERVED_ONLY]`** / **`[ASSUMED_NOT_ENFORCED]`** | Redis Cluster gossip election favors higher offset via delay timer, but split-brain or manual failovers can override. |
| **6. Network Partitions** | Minority partition cannot accept writes that contradict majority partition consensus. | **`[ASSUMED_NOT_ENFORCED]`** (Redis Cluster is an AP system without quorum writes) | Split-brain writes in minority master are wiped upon cluster re-merge, causing over-admission. |
| **7. Simultaneous Failures** | At most one node in a shard fails per replication epoch; simultaneous primary/replica failure is impossible. | **`[ASSUMED_NOT_ENFORCED]`** (Requires independent failure domains/power supplies) | Complete loss of in-memory shard state; forces cold-start reset. |
| **8. Slot Ownership Migration** | Key hash tags `{clientId}` strictly map state and idempotency to identical single-master slots. | **`[ENFORCED_BY_IMPLEMENTATION]`** (`formatStateKey` and `formatIdempKey`) | Multi-client atomic operations trigger `CROSSSLOT` rejection. |
| **9. Clock Slew & Drift** | Host operating system clocks never step forward; clock drift is bounded by $|\delta| \le 5\,\text{ms}$. | **`[ENFORCED_BY_DEPLOYMENT_INFRA]`** (`chronyd` configured with `makestep 0`) | Forward clock jumps bypass rate limiting, producing up to $+704.29$ tokens excess. |
| **10. Ambiguous Retries** | Client timeouts trigger retries with identical idempotency key and payload fingerprint. | **`[ENFORCED_BY_IMPLEMENTATION]`** & **`[ENFORCED_BY_DEPLOYMENT_INFRA]`** | Retry without idempotency triggers duplicate debit; retry with conflicting payload rejected. |

---

## 3. Deep-Dive on Non-Enforced Dependencies

### 3.1 Highest-Offset Election (Redis Specification Gap)
In academic consensus protocols (Raft, Paxos), leader election mathematically guarantees that a candidate lacking committed log entries cannot become leader.

**In Redis Cluster:**
- Replicas with higher offsets initiate elections slightly earlier (a random delay plus `(offset_diff) * offset_delay_factor`).
- However, if the lagging replica's election vote request reaches a majority of cluster masters first (e.g. due to asymmetric network latency), **the lagging replica will be elected master**.
- In Redis Cluster, `min-replicas-to-write` and `min-replicas-max-lag` can stop writes if replicas are disconnected, but **do not guarantee linearizable leader election**.
- **Conclusion:** Highest-offset election in Redis Cluster is **probabilistic**, not guaranteed.

### 3.2 Redis Cluster as an AP Partition Model
Under network partitions, Redis Cluster adheres to Brewer's CAP Theorem as an **AP system** (Availability over Consistency):
- A partitioned master will continue accepting writes for `cluster-node-timeout` (default: 15 seconds) before flagging itself failing.
- Concurrently, the majority partition promotes a replica.
- Once the partition heals, the old master is demoted to a replica and executes a full sync (`PSYNC`), completely discarding all un-replicated writes accepted during the partition.
- **Conclusion:** Strict rate limiting consistency across network partitions is **fundamentally impossible** on standard Redis Cluster without external distributed locking or synchronous proxy fencing.

---

## 4. Definitive Scoped Model Definition: $\mathcal{M}_{\text{DREB-Restricted}}$

The mathematical guarantees of DREB-RCA70 hold if and only if the operational environment conforms to the **Restricted Deployment Model $\mathcal{M}_{\text{DREB-Restricted}}$**:

$$\mathcal{M}_{\text{DREB-Restricted}} = \left\{
\begin{array}{ll}
\text{Architecture:} & \text{Single-Master / Dual-Replica with rack-isolated power} \\
\text{Connection:} & \text{Connection-pinned socket executing EVAL followed by WAIT 1 } t_{\text{wait}} \\
\text{Host Clocks:} & \text{Monotonic NTP slew daemon (chrony makestep 0), bounding } |\delta| \le 5\text{ ms} \\
\text{Persistence:} & \text{appendfsync everysec with battery-backed memory or appendfsync always} \\
\text{Network:} & \text{Cluster node timeout } \le 1.0\text{ s with min-replicas-to-write 1} \\
\text{Client Layer:} & \text{Idempotent retry with SHA-256 payload binding on AMBIGUOUS_TIMEOUT}
\end{array}
\right\}$$

If an enterprise deployment satisfies $\mathcal{M}_{\text{DREB-Restricted}}$, DREB-RCA70 preserves G1 across single-node failovers. If deployed on un-fenced, asynchronous, or stepping-clock environments, G1 cannot be guaranteed.
