# CLAIM INTEGRITY AUDIT AND EVIDENCE PROVENANCE REGISTER

**Target Venue:** 4th IEEE NICE-TEAS ASIA 2026, Hanoi, Vietnam  
**Manuscript:** *Rest-Conditioned Adaptive Burst Limiting for Multi-Tenant API Gateways: Formal Bounds and Adversarial Evaluation*  
**Canonical Research Freeze:** Commit `1040cc4` (`research/systematic-improvement`)  
**Audit Date:** 12 November 2026  
**Auditor:** Quality Control and Verification Panel  

---

## 1. Executive Summary

This document performs an exhaustive, line-by-line verification of all quantitative, architectural, and mathematical claims presented in the IEEE NICE-TEAS ASIA 2026 conference adaptation of DREB-RCA70. 

Every claim is cross-referenced directly against:
1. Frozen research commit `1040cc4` and canonical manuscript [`DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md).
2. Phase 9.1 Red-Team Audit Errata ([`PHASE9_1_MECHANISM_TERMINOLOGY_ERRATUM.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_MECHANISM_TERMINOLOGY_ERRATUM.md), [`PHASE9_1_WORKLOAD_PROVENANCE_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_WORKLOAD_PROVENANCE_AUDIT.md), [`PHASE9_1_FARMING_METRIC_PROVENANCE.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_FARMING_METRIC_PROVENANCE.md), [`PHASE9_1_MEMORY_ACCOUNTING_RECONCILIATION.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_MEMORY_ACCOUNTING_RECONCILIATION.md)).
3. Phase 9.2 Claim Freeze Manifest ([`PHASE9_2_CLAIM_FREEZE_MANIFEST.json`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_2_CLAIM_FREEZE_MANIFEST.json)).

### **Audit Verdict: 100% GROUNDED / ZERO DRIFT / ZERO UNSUPPORTED CLAIMS (PASS)**

---

## 2. Claim-by-Claim Verification Matrix

```
+-----------------------------------------------------------------------------------------------------------------------------------------------+
| Table 1: Comprehensive Evidence Provenance and Claim Verification Matrix                                                                      |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| #  | Manuscript Claim               | Exact Numerical / Mathematical Scope | Frozen Source Reference         | Verification Status            |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 1  | **Workload Provenance**        | 10 programmatic synthetic workloads  | Phase 9.1 Workload Provenance   | **VERIFIED (PASS)**            |
|    |                                | (W01–W10) and 17 closed-loop attack  | Audit; §6.1, §6.2.              | Strictly synthetic; no claim of|
|    |                                | families (A01–A17) across 30 seeds.  | CSV: `adversarial_farming.csv`  | production enterprise traces.  |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 2  | **Farming Suppression (93.0%)**| 93.04% credit-accrual rate reduction | Phase 9.1 Metric Provenance;    | **VERIFIED (PASS)**            |
|    |                                | in parameter ablation `rec_010`.     | CSV: `rca_ablation_summary.csv` | Scoped strictly to accrual rate|
|    |                                | Baseline: 50.0 t/s -> RCA70: 3.48 t/s| Line 14, `rec_010` row.         | in ablation, not gen. traffic. |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 3  | **Throughput Suppression**     | Workload-specific: W04: 81.9%,       | Phase 9.1 Metric Provenance;    | **VERIFIED (PASS)**            |
|    |                                | W08: 56.7%, W10: 85.7%.              | Manifest: Section 3.            | Explicitly broken down; mean   |
|    |                                | Range: 56.7% to 85.7% (mean 74.8%).  | CSV: `adversarial_farming.csv`  | 74.8% stated as evaluated mean.|
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 4  | **Burst Ceiling vs. Capacity** | Idle pool ceiling: I_ceil(K) in      | Phase 9.1 Terminology Erratum;  | **VERIFIED (PASS)**            |
|    |                                | [0, 450]. Total burst capacity:      | Canonical: Section 3.           | Formula C_burst = B_max + I/d  |
|    |                                | C_burst_max(K) = 700 + I_ceil(K)/1.5.| Equation (3), (4).              | rigorously maintained.         |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 5  | **Benign Quiescent Burst FRR** | 0.00% False Rejection Rate on 1,000- | Canonical: Section 7;           | **VERIFIED (PASS)**            |
|    |                                | token batch bursts after 20 s rest.  | CSV: `workload_results.csv`     | Scoped to quiescent condition; |
|    |                                | Baseline Dual-Bucket FRR: 30.00%.    | W01 quiescent burst row.        | zero FRR confirmed.            |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 6  | **Pareto Frontier Scope**      | Scope 1 (c <= 700): Static Dual      | Phase 9.1 Pareto Audit;         | **VERIFIED (PASS)**            |
|    |                                | Bucket sole frontier. Scope 2        | Canonical: Section 8;           | Dual-Bucket dominance in S1    |
|    |                                | (c = 1,000): DREB-RCA70 + Token Bkt. | Figure 2 (`fig2_pareto.pdf`).   | explicitly stated & defended.  |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 7  | **Theorem 1: Local Rate (G1)** | A(T) <= 1285.7143 + 1000.0 * T       | Canonical: Section 4.1;         | **VERIFIED (PASS)**            |
|    |                                | Under monotonic local server clock.  | Inductive Proof Appendix A.1.   | Exact closed-form coefficients.|
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 8  | **Theorem 2: Clock Skew (G1d)**| A(t) <= 1295.7143 + 1000.0 * t       | Canonical: Section 4.2;         | **VERIFIED (PASS)**            |
|    |                                | Under bounded skew |\Delta\tau|<=10ms| Proof Appendix A.2.             | 10-token allowance identified. |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 9  | **Theorem 5: Failover (G5)**   | A_event_burst <= 2 C_max = 2,000     | Canonical: Section 4.3;         | **VERIFIED (PASS)**            |
|    |                                | tokens per client key.               | Proof Appendix A.3.             | Strictly per client key; not   |
|    |                                | Single-crash isolated discontinuity. | Figure 6 (`fig6_failover.pdf`). | cluster-wide aggregate.        |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 10 | **Theorem 6: Convergence (G2)**| \lim N_E(T)/T = 0 is sufficient      | Canonical: Section 4.4;         | **VERIFIED (PASS)**            |
|    |                                | universally; necessary under sustain-| Phase 7.3.4 Erratum;            | Qualified condition; not an    |
|    |                                | ed saturated adversarial demand.     | Proof Appendix A.4.             | unqualified "if and only if".  |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 11 | **Theorem 3: Recovery (G3)**   | \lambda_rec = 0.05 s^-1. Recovery to | Canonical: Section 3.2;         | **VERIFIED (PASS)**            |
|    |                                | baseline (K=0) in 20.0 seconds.      | Figure 4 (`fig4_recovery.pdf`). | Exact parameters verified.     |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 12 | **Latency Hierarchy (Tiers)**  | Tier 1 JMH: 42 ns P50; Tier 2 Async: | Canonical: Section 5.3, §7.3;   | **VERIFIED (PASS)**            |
|    |                                | 0.259 ms P50, 0.667 ms P99;          | CSV: `latency_hierarchy.csv`;   | WAIT 1 P99 (1.301 ms) is NEVER |
|    |                                | Tier 3 WAIT 1: 0.348 ms P50, 1.301 ms| Figure 5 (`fig5_latency.pdf`).  | called sub-millisecond.        |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 13 | **WAIT 1 Durability Semantics**| Synchronous volatile replica ack.    | Canonical: Section 5.2;         | **VERIFIED (PASS)**            |
|    |                                | In-memory acknowledgement; not       | Section 5.2 PACELC trade-off    | Non-consensus volatile nature  |
|    |                                | disk fsync or consensus (Raft/Paxos).| analysis.                       | explicitly documented.         |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 14 | **Idempotency Model**          | 5-condition contract: client reqId,  | Canonical: Section 5.1;         | **VERIFIED (PASS)**            |
|    |                                | hash deduplication, TTL window, retry| Contract definition (1)–(5).    | Exactly-once requires client-  |
|    |                                | cache. At-least-once transport.      |                                 | side request ID adherence.     |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 15 | **Redis Memory Footprint**     | ~392 MB per million records in a     | Phase 9.1 Memory Reconciliation | **VERIFIED (PASS)**            |
|    |                                | scenario containing 1M state hashes  | Report; Canonical: Section 5.4. | Explicitly framed as scenario  |
|    |                                | and 1M request-id cache keys.        |                                 | capacity, not auto overhead.   |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
| 16 | **Production Readiness Scope** | Academic reference prototype.        | Canonical: Section 9;           | **VERIFIED (PASS)**            |
|    |                                | Evaluated on 6-node cluster testbed; | Phase 9.2 Claim Manifest.       | Zero claim of production       |
|    |                                | not claimed as production-certified. |                                 | certification.                 |
+----+--------------------------------+--------------------------------------+---------------------------------+--------------------------------+
```

---

## 3. Preservation of Negative & Non-Trivial Results

The manuscript preserves all empirical trade-offs without glossing over unfavorable findings:
1. **Scope 1 Trade-off:** The manuscript explicitly reports that for small bursts ($c \le 700$), Static Dual Bucket achieves superior farming suppression (1.00 relative score) compared to DREB-RCA70, because DREB deliberately retains elasticity.
2. **Deficit Penalty Trade-off:** The asymmetric penalty ratio ($d = 1.5$) consumes 1.5 idle credits for every deficit token admitted, which accelerates credit depletion during prolonged bursts.
3. **Replication Latency Penalty:** While asynchronous Redis dispatch achieves 0.667 ms P99, enabling synchronous `WAIT 1` increases P99 latency to **1.301 ms** (a 1.95$\times$ increase), representing a fundamental PACELC latency-durability trade-off.
4. **Synthetic Scope Boundary:** The manuscript states unequivocally in the abstract, methodology, and limitations sections that all evaluations are conducted on programmatic synthetic workloads and algorithmic probing attacks, not live commercial gateway traffic.
