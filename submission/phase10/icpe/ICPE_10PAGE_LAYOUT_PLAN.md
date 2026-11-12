# ICPE 2027: 10-PAGE CONDENSATION AND SECTION BUDGET PLAN

**Target Venue:** ACM/SPEC International Conference on Performance Engineering (ICPE 2027)  
**Track:** Research Track (Full Paper)  
**Strict Page Limit:** 10 pages in ACM double-column format (`acmart`, `sigconf`), **excluding references and appendices**.  
**Auditor:** Publication Preparation Review Panel  
**Source Manuscript:** [`DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md) (~12.5 pages)  

---

## 1. Executive Strategy

ICPE enforces a strict **10-page limit** for the main narrative (excluding references and appendices). The canonical Phase 9.2 manuscript currently spans approximately 12.5–13 ACM pages.

To comply with ICPE 2027 formatting rules without sacrificing technical substance or weakening reviewer confidence:
1. **Core Narrative (Pages 1–10):** Focus on the systems architecture, the rest-conditioned mechanism, primary empirical results, benchmark performance, and Pareto trade-offs.
2. **Theorem Proofs Relocated to Appendix A:** Present concise formal theorem statements and intuitive proof sketches in Section 4, moving exhaustive algebraic proofs and step-by-step inductive derivations to **Appendix A**.
3. **Supplementary Workload & Lua Details Relocated to Appendix B:** Move full 17-attack parameter breakdowns, extended JMH latency tables (P99.9/P99.99), and Redis Lua source code listings to **Appendix B**.
4. **Appendices:** ICPE allows appendices beyond the 10-page limit for reviewers' supplementary reading, provided the 10-page paper is self-contained.

---

## 2. Target Section-by-Section Page Budget (Total: Exactly 10 Pages)

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: ICPE 2027 10-Page Structural Allocation Budget                                                 |
+---------+----------------------------------------------+---------------+--------------------------------+
| Section | Title / Content                              | Current (p.)  | Target Budget (p.) | Reduction  |
+---------+----------------------------------------------+---------------+--------------------+-----------+
| 0       | Title, Abstract, ACM CCS, Keywords, Intro    | 1.50          | 1.25               | -0.25 p.  |
| 1       | Problem Formulation & Threat Model           | 1.25          | 1.00               | -0.25 p.  |
| 2       | The DREB-RCA70 Mechanism Architecture        | 1.75          | 1.50               | -0.25 p.  |
| 3       | Formal Theoretical Guarantees (Sketches)     | 2.25          | 1.25               | -1.00 p.  |
| 4       | Distributed Redis Cluster Implementation     | 1.25          | 1.00               | -0.25 p.  |
| 5       | Experimental Methodology & Workloads         | 1.00          | 0.75               | -0.25 p.  |
| 6       | Empirical Performance Evaluation             | 2.25          | 2.25               | 0.00 p.   |
| 7       | Pareto Frontier & Multi-Objective Trade-offs | 0.75          | 0.50               | -0.25 p.  |
| 8       | Related Work & Limitations                   | 0.75          | 0.50               | -0.25 p.  |
+---------+----------------------------------------------+---------------+--------------------+-----------+
| TOTAL   | Main Narrative Body                          | 12.75 p.      | **10.00 p.**       | **-2.75** |
+---------+----------------------------------------------+---------------+--------------------+-----------+
| Post-10 | References (Uncounted)                       | ~1.25 p.      | ~1.25 p.           | N/A       |
| Post-10 | Appendix A: Mathematical Derivations         | New Appendix  | ~2.50 p.           | N/A       |
| Post-10 | Appendix B: Workload & Implementation Tables | New Appendix  | ~1.50 p.           | N/A       |
+---------+----------------------------------------------+---------------+--------------------+-----------+
```

---

## 3. Detailed Relocation Breakdown

### 3.1 Content Retained in the 10-Page Main Body
- **Introduction & Motivation:** The Quiescence Paradox, operational tension, and summary of 5 contributions.
- **System & Threat Model:** Formal definition of client requests, cost, sustained rate $R$, nominal burst capacity $C_{\max}$, and the credit-farming threat model.
- **The DREB Mechanism:** Equations for token bucket $B$, auxiliary idle pool $I$, asymmetric deficit penalty $d=1.5$, abuse score $K(t)$, and rest-conditioned ceiling $I_{\text{ceil}}(K)$.
- **Theoretical Guarantees (Summary & Sketches):**
  - **Theorem 1 ($G_1$ Local-Time Bound):** State theorem equation and 1-paragraph proof intuition; state reference to Appendix A.1 for full inductive proof.
  - **Theorem 2 ($G_{1,\delta}$ Clock-Skew Envelope):** State physical-time bound under bounded skew $|\Delta \tau| \le 10$ ms; cross-reference Appendix A.2.
  - **Theorem 3 ($G_5$ Single-Event Bound):** State $A_{\text{event\_burst}} \le 2,000$ tokens per key bound; cross-reference Appendix A.3.
  - **Theorem 4 ($G_2$ Asymptotic Rate Convergence):** State sufficiency and conditional necessity theorem; cross-reference Appendix A.4.
- **Redis Cluster Architecture:** Dual-hash layout, hash-tag `{clientId}` locality, atomic Lua script execution, and 5-condition Idempotency Contract.
- **Empirical Evaluation (Core Results Preserved):**
  - 10-workload benchmark results (0.00% FRR on benign 1,000-token batch bursts).
  - Closed-loop adversarial farming suppression (56.7%–85.7% throughput reduction; 93.04% credit accrual rate reduction).
  - 4-tier latency hierarchy table (42 ns in-memory P50, 0.259 ms async P50, 0.348 ms `WAIT 1` P50).
  - Zero-allocation memory footprint and CPU utilization scaling.
- **Pareto Trade-Off Reconciliation:** Multi-objective table comparing Token Bucket, Static Dual Bucket, and DREB-RCA70 across low-cost ($c \le 700$) vs high-cost ($c = 1,000$) scopes.
- **Related Work & Conclusion:** Compact survey of classical token buckets, dual buckets, adaptive rate limiters, and concluding remarks.

### 3.2 Content Relocated to Appendix A (Mathematical Proofs)
1. **Appendix A.1 (Full Proof of Theorem 1 / $G_1$ Bound):**
   - Step-by-step induction over interval $[0, T]$ with arbitrary request arrival sequences.
   - Formal handling of bucket replenishment discontinuities.
2. **Appendix A.2 (Derivation of Physical-Time Envelope $G_{1,\delta}$):**
   - Derivation of the maximum 10-token local clock expansion under $|\Delta \tau| \le 10$ ms offset.
3. **Appendix A.3 (Full Proof of Distributed Single-Event Bound $G_5$):**
   - Complete formal proof establishing that single-crash Redis failover admits at most $2 C_{\max} = 2,000$ tokens per client key.
   - Proof that $K(t)$ decay during failover is strictly non-negative.
4. **Appendix A.4 (Proof of $G_2$ Sufficiency and Necessity):**
   - Formal $\epsilon$-$\delta$ proof that sublinear event rate is sufficient for asymptotic convergence.
   - Construction demonstrating necessity under sustained saturated adversarial request demand.

### 3.3 Content Relocated to Appendix B (Extended Experimental & Engineering Artifacts)
1. **Appendix B.1 (Full Workload Parameterization):**
   - Detailed configuration parameters for all 10 synthetic workloads (arrival distributions, burst sizes, idle sleep intervals).
2. **Appendix B.2 (Full 17-Attack Parameter Matrix):**
   - Complete table of the 17 closed-loop attack families, including probing periods, token burn rates, and adaptive phase shifts.
3. **Appendix B.3 (Extended Tail Latency Distributions):**
   - High-percentile latency tables (P99.9, P99.99, P99.999) under synthetic network jitter and Redis cluster stress.
4. **Appendix B.4 (Atomic Lua Script Implementation):**
   - Annotated Redis Lua script showing atomic state loading, time delta computation, deficit penalty application, and TTL setting.

---

## 4. Typography and Formatting Optimization Rules

To ensure strict compliance with the 10-page limit:
1. **Figures and Tables:**
   - Place full-width figures/tables at the top of pages (`figure*`, `table*`).
   - Use compact table formatting (`\small` font, trimmed `\tabcolsep`).
2. **Equations:**
   - Consolidate multi-line piecewise equations where appropriate using `align` or `matrix` notation.
   - Use inline mathematical expressions for scalar definitions.
3. **Lists and Enumerations:**
   - Use compact `enumitem` styling (`[noitemsep, topsep=2pt]`) to eliminate extraneous vertical whitespace.
4. **References:**
   - Ensure references are uncounted in accordance with ICPE guidelines. Use standard ACM reference formatting.

---

## 5. Verification Checklist for the 10-Page Format

- [ ] Main narrative (Sections 1 through 8) terminates before or on page 10, column 2.
- [ ] References begin immediately on or after page 10, without contributing to the 10-page narrative cap.
- [ ] Appendices are positioned after the References section and clearly labeled as supplementary reading.
- [ ] No essential claims or experimental findings are omitted; all relocated items are fully preserved and cross-referenced in Appendices A and B.
