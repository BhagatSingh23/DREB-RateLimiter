# PHASE 9.1: FINAL INDEPENDENT PRE-SUBMISSION RED-TEAM AUDIT DECISION

**Audit Date:** November 2026  
**Repository Branch:** `research/systematic-improvement`  
**Starting Commit:** `c8df6d4`  
**Target Policy:** DREB-RCA70  
**Auditor:** Independent Red-Team Review Panel  

---

## 1. Executive Summary and Final Recommendation

The independent pre-submission red-team audit of the DREB-RCA70 manuscript, figures, and reproducibility artifact has concluded.

### Final Recommendation: **HOLD (CONDITIONAL)**

The previous Phase 9 recommendation of **SUBMIT (GO)** is **OVERTURNED and SUSPENDED**.

While the core algorithmic mechanism, mathematical theorems ($G_1, G_{1,\delta}, G_2, G_3$), Redis Cluster Lua adapter, and basic empirical datasets are technically sound, the Phase 9 manuscript draft contains **critical unsubstantiated claims and terminology errors** that would guarantee immediate rejection or severe peer-review distress at premier academic venues:
1. **False Claim of Real-World Traces:** Claimed evaluation on "19 workloads containing 8 real-world microservice traces derived from production telemetry." In reality, workloads W11–W19 do not exist, and W01–W10 are 100% synthetic programmatic generators.
2. **Conflated Memory Terminology:** Reported 56 bytes/client as "retained heap," whereas 56 bytes is merely the shallow isolated `ClientState` object; true retained heap in `ConcurrentHashMap` is $150.6 - 165.3\,\text{bytes/client}$.
3. **Mechanism Ambiguity:** Labeled $C_{\text{eff}}(K)$ as the "total burst ceiling" rather than the **adaptive idle-credit pool ceiling $I_{\text{ceil}}(K)$**, obscuring the fact that baseline liquid capacity $B_{\max} = 700$ remains intact.
4. **Unqualified 93.1% Farming Suppression:** Conflated idle-credit accrual reduction in parameter ablation with total request traffic suppression across workloads ($56.7\% - 85.7\%$).
5. **Over-Promised Latency Claims:** Characterized distributed Redis execution as "sub-millisecond" without disclosing that synchronous replication durability (`WAIT 1`) exhibits a P99 tail latency of **$1.301\,\text{ms}$** ($+95.1\%$ penalty).

**Condition for Lift:** A revised manuscript draft ([`DREB_RCA70_MANUSCRIPT_REVISED.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/DREB_RCA70_MANUSCRIPT_REVISED.md)) has been prepared that systematically corrects all five discrepancies, provides rigorous provenance for all metrics, and adopts conservative, defensive terminology.

---

## 2. Independent Evaluation Matrix

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: Independent Red-Team Evaluation Matrix by Research Dimension.                                 |
+------------------------------------+---------------+----------------------------------------------------+
| Dimension                          | Rating        | Key Evidence & Findings                            |
+------------------------------------+---------------+----------------------------------------------------+
| **1. Workload Provenance**         | **FAIL**      | W11-W19 non-existent. W01-W10 are purely synthetic |
|                                    | (in Draft)    | generators. Claim of "real-world traces" is false. |
|                                    | **PASS** (Rev)| Corrected to 10 synthetic workloads + 17 attacks.  |
+------------------------------------+---------------+----------------------------------------------------+
| **2. Mathematical Correctness**    | **PASS**      | Theorems 1-6 verified. Local G1, physical G1_delta,|
|                                    |               | 2,000-token single-event bound, and G2 limits hold.|
+------------------------------------+---------------+----------------------------------------------------+
| **3. Novelty & Related Work**      | **PASS**      | Taxonomy table, RFC citations, and CBS comparison  |
|                                    |               | accurate. Clear distinction from static limiters.  |
+------------------------------------+---------------+----------------------------------------------------+
| **4. Experimental Claim Integrity**| **CONDITIONAL**| W04 (81.9%), W08 (56.7%), W10 (85.7%) verified.    |
|                                    |               | 93.1% qualified as credit accrual reduction.       |
+------------------------------------+---------------+----------------------------------------------------+
| **5. Figure Correctness**          | **CONDITIONAL**| Figures 2-6 fully verified. Figure 1 axis corrected|
|                                    |               | from C_eff to I_ceil(K).                           |
+------------------------------------+---------------+----------------------------------------------------+
| **6. Memory Accounting**           | **PASS** (Rev)| Separates 40B payload, 56B shallow, 150-165B heap, |
|                                    |               | 216B Redis hash, 176B idempotency.                 |
+------------------------------------+---------------+----------------------------------------------------+
| **7. Latency Accounting**          | **PASS** (Rev)| Clearly notes WAIT 1 P99 is 1.301 ms (+95.1% tail),|
|                                    |               | reserving "sub-millisecond" strictly for async.    |
+------------------------------------+---------------+----------------------------------------------------+
| **8. Artifact Reproducibility**    | **PASS**      | Functional runner script passes 100%. Checksums OK.|
|                                    |               | All tests pass without modifying historical data.  |
+------------------------------------+---------------+----------------------------------------------------+
| **9. Venue Compliance**            | **PASS**      | Aligns with ACM double-blind, CCS, and page limits.|
|                                    |               | Reassessed SoCC vs. ICPE fit.                      |
+------------------------------------+---------------+----------------------------------------------------+
| **OVERALL READINESS**              | **HOLD**      | Requires review and sign-off on the revised draft. |
+------------------------------------+---------------+----------------------------------------------------+
```

---

## 3. Venue Reassessment: ACM SoCC vs. ACM ICPE

The Phase 8 and Phase 9 documents positioned ACM SoCC as the primary target and characterized it as high-probability. The red-team panel reassessed this strategy:

1. **ACM Symposium on Cloud Computing (SoCC):**
   - *Strengths:* Focus on cloud API gateways, Redis Cluster distributed systems, and PACELC replication trade-offs aligns well with SoCC.
   - *Vulnerabilities:* Top-tier cloud systems reviewers at SoCC are notoriously demanding regarding production trace validation. Given that our evaluation is conducted purely on synthetic generators and simulated closed-loop attacks, reviewers may discount empirical claims unless framed defensively as algorithmic models.
   - *Competitiveness:* SoCC acceptance rates are historically ~20–25%. It should never be described as "high probability."
2. **ACM International Conference on Performance Engineering (ICPE):**
   - *Strengths:* ICPE explicitly prioritizes measurement rigor, microbenchmarking methodology, multi-tier latency dissection (Scope A to D), and reproducible artifacts. The combination of nanosecond JMH harnesses, Jqwik invariant testing, and Redis network evaluation is an exceptional fit for ICPE's Research Track.
   - *Strategic Recommendation:* Prepare the submission for **ACM SoCC 2027** with conservative synthetic disclosures, while maintaining **ACM ICPE 2027** as an equally prestigious alternative target.

---

## 4. Phase 9.1 Deliverable Audit Inventory

The following eight audit reports and corrected artifacts have been generated in the repository root:

1. [`PHASE9_1_WORKLOAD_PROVENANCE_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_WORKLOAD_PROVENANCE_AUDIT.md): Debunks the 19-workload / real-world trace claim; documents exact code provenance of W01–W10 and Families A–E.
2. [`PHASE9_1_MEMORY_ACCOUNTING_RECONCILIATION.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_MEMORY_ACCOUNTING_RECONCILIATION.md): Reconciles 56-byte shallow instance size vs. 150–165 byte retained heap, and details 1M-client Redis sizing (~392 MB).
3. [`PHASE9_1_MECHANISM_TERMINOLOGY_ERRATUM.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_MECHANISM_TERMINOLOGY_ERRATUM.md): Formalizes the distinction between idle ceiling $I_{\text{ceil}}(K)$ and total burst capacity $C_{\text{burst\_max}}(K)$.
4. [`PHASE9_1_FARMING_METRIC_PROVENANCE.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_FARMING_METRIC_PROVENANCE.md): Re-derives the exact calculations for 81.9% (W04), 56.7% (W08), 85.7% (W10), and qualifies 93.1% as credit accrual throttling.
5. [`PHASE9_1_PARETO_AND_FIGURE_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_PARETO_AND_FIGURE_AUDIT.md): Validates Scope 1, 2, and 3 Pareto frontiers and audits all six publication figures.
6. [`PHASE9_1_ARTIFACT_REPRODUCIBILITY_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_ARTIFACT_REPRODUCIBILITY_AUDIT.md): Verifies functional reproducibility (100% test pass, clean checksums) vs. numerical data-driven reproducibility.
7. [`PHASE9_1_LATENCY_AND_PERFORMANCE_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_LATENCY_AND_PERFORMANCE_AUDIT.md): Dissects Scopes A–D; enforces that `WAIT 1` P99 (1.301 ms) is not labeled sub-millisecond.
8. [`PHASE9_1_FINAL_SUBMISSION_DECISION.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_1_FINAL_SUBMISSION_DECISION.md): This decision document.

---

## 5. Next Steps for Authors

1. Review and adopt the corrected manuscript: [`DREB_RCA70_MANUSCRIPT_REVISED.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/DREB_RCA70_MANUSCRIPT_REVISED.md).
2. Maintain the original [`DREB_RCA70_MANUSCRIPT_DRAFT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/DREB_RCA70_MANUSCRIPT_DRAFT.md) untouched as an audit trail.
3. Await independent co-author review before deciding on the final venue submission schedule.
