# ACM SoCC 2027: SUBMISSION STRATEGY AND SYSTEMATIC READINESS PLAN

**Target Venue:** ACM Symposium on Cloud Computing (SoCC 2027)  
**Format:** ACM double-column format (`acmart`, `sigconf`), **12 pages excluding references**  
**Review Process:** Double-anonymous peer review  
**Status:** **RECOMMENDED FIRST CHOICE**  
**Auditor:** Publication Preparation Review Panel  
**Source Manuscript:** [`DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md)  

---

## 1. Why ACM SoCC is the Premier Target

ACM SoCC is co-sponsored by ACM SIGMOD and ACM SIGOPS. It is the premier academic forum explicitly dedicated to cloud systems, distributed data management, and virtualization/gateway infrastructure.

DREB-RCA70 addresses a foundational cloud systems problem:
1. **Multi-Tenant Gateway Admission:** Operates at the entry point of modern cloud microservice meshes (e.g., Envoy, Kong, AWS API Gateway).
2. **Cloud Infrastructure Data Layer:** Evaluates Redis Cluster distributed in-memory key-value state, failover bounds, replication delay, and `WAIT 1` consistency under the PACELC theorem.
3. **Operational Robustness:** Directly solves the Quiescence Paradox where bursty cloud tenants are unfairly penalized while low-rate attackers abuse shared downstream cloud databases.
4. **Natural Page Budget Fit:** SoCC's 12-page budget (excluding references) perfectly matches the current canonical manuscript (~12 pages). It requires zero truncation of mathematical derivations or distributed systems failure analysis.

---

## 2. Page Allocation and Structural Fit (12-Page Limit)

The canonical manuscript [`DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md) was drafted to align directly with the standard 12-page SoCC layout:

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: ACM SoCC 12-Page Natural Structural Allocation                                                 |
+---------+----------------------------------------------+---------------+--------------------------------+
| Section | Title / Content                              | Pages (Est.)  | Narrative Function             |
+---------+----------------------------------------------+---------------+--------------------------------+
| 1       | Introduction & Motivation (Quiescence Paradox| 1.50 p.       | Core motivation, 5 key claims  |
| 2       | Problem Formulation & Threat Model           | 1.25 p.       | Gateway model, credit farming  |
| 3       | The DREB-RCA70 Architecture & Parameters     | 1.75 p.       | Dual-rate, K(t) recovery, d=1.5|
| 4       | Formal Theoretical Guarantee Contract        | 2.25 p.       | G1–G6 proofs & failover bounds |
| 5       | Distributed Redis Cluster Implementation     | 1.50 p.       | Lua engine, WAIT 1, PACELC     |
| 6       | Experimental Methodology & Workload Suite    | 1.00 p.       | 10 workloads, 17 attack suites |
| 7       | Empirical Evaluation & Latency Hierarchy     | 2.25 p.       | JMH & cluster latency, attacks |
| 8       | Pareto Frontier & Multi-Objective Trade-offs | 0.75 p.       | Scope 1 & Scope 2 Pareto curves|
| 9       | Related Work, Limitations & Conclusion       | 0.75 p.       | Classical & dynamic limiters   |
+---------+----------------------------------------------+---------------+--------------------------------+
| TOTAL   | Main Paper Body                              | **12.00 p.**  | Exactly 12 Pages               |
+---------+----------------------------------------------+---------------+--------------------------------+
| Post-12 | References (Uncounted)                       | ~1.25 p.      | Peer-reviewed bibliography     |
+---------+----------------------------------------------+---------------+--------------------------------+
```

---

## 3. Anticipated Reviewer Concerns and Strategic Defenses

| Potential Reviewer Objection | Strategic Defense Incorporated in Canonical Manuscript |
| :--- | :--- |
| **"Evaluation is based on programmatic synthetic workloads rather than real production enterprise gateway traces."** | **Defense:** Section 9 explicitly acknowledges the lack of proprietary production traces as a research limitation. It justifies the synthetic workloads by demonstrating that they span 10 rigorous, parameterized Poisson, Pareto, burst-sleep, and multi-tenant profiles with 30 independent seeds, and the 17 closed-loop attacks model an adaptive adversary probing dynamic rate limiters. |
| **"Why not simply use downstream queue backpressure or feedback-driven rate limiting?"** | **Defense:** Section 1 and Section 9 explain that downstream queue backpressure introduces multi-hop network round-trip delays, requires complex cross-service signaling, and fails to stop sub-second micro-bursts before they enter the cluster. DREB-RCA70 polices admissions at line-rate (42 ns in-memory) at the edge proxy. |
| **"How does Redis failover impact token accounting if asynchronous replication drops state?"** | **Defense:** Section 4 and Section 5 rigorously formulate the Single-Crash Failover Guarantee ($G_5$), mathematically proving that over-admission during failover is strictly capped at $2 C_{\max} = 2,000$ tokens per key, and empirically profile `WAIT 1` synchronous durability (0.348 ms P50). |

---

## 4. Preparation Timeline for SoCC 2027

Since SoCC 2026 has concluded (November 2026) and SoCC 2027 call-for-papers has not yet been issued:
- **Q1 2027:** Monitor ACM SoCC official website (`acmsocc.org`) for CFP release and PC Chair announcements.
- **Q1–Q2 2027:** Human co-authors review canonical manuscript, resolve author metadata, and execute dry-run artifact packaging.
- **Summer 2027 (Expected June/July):** Submit paper via HotCRP before the abstract and paper deadlines.
- **Fall 2027 (Expected October/November):** Attend SoCC 2027 conference to present findings and defend artifact evaluation.
