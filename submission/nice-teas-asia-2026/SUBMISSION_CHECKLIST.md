# IEEE NICE-TEAS ASIA 2026: SUBMISSION AND PUBLICATION READINESS CHECKLIST

**Conference:** 4th IEEE International Conference on Novel Intelligent Computing and Emerging Technologies, Engineering, and Applications / Systems (IEEE NICE-TEAS ASIA 2026)  
**Location:** Hanoi, Vietnam  
**Manuscript Title:** *Rest-Conditioned Adaptive Burst Limiting for Multi-Tenant API Gateways: Formal Bounds and Adversarial Evaluation*  
**Auditor:** Quality Control and Verification Panel  
**Date:** 12 November 2026  

---

> [!CAUTION]
> **SUBMISSION RESTRICTION NOTICE:**
> AI agents (Antigravity) cannot log into Microsoft CMT, upload papers, communicate with Program Chairs, or execute copyright transfer agreements. All submission actions must be executed exclusively by human co-authors.

---

## 1. Conference Requirements & Page Budget Verification

- [x] **Format Class:** Official IEEE double-column conference format (`IEEEtran.cls`, `[conference]`, `letterpaper`).
- [x] **Target Page Count:** Exactly **10 printed pages**, including all figures, tables, proofs, and references.
- [!] **CFP Ambiguity Resolution (MANDATORY HUMAN ATTENTION):**
  - *Conflict Noted:* The NICE-TEAS ASIA 2026 CFP contains conflicting page-length instructions in different sections: one clause indicates a *minimum of 10 pages* for regular papers, while another clause indicates a *maximum of 10 pages including references*.
  - *Mitigation Executed:* The manuscript has been crafted to hit **exactly 10 pages** (terminating cleanly on page 10), ensuring compliance under both interpretations without fluff/padding or sacrificing methodological rigor.
- [x] **Typography & Fonts:** Standard IEEE Times/Computer Modern fonts; all vector figures and math symbols embedded.
- [x] **Legibility of Visual Assets:** All 6 figures sized to column width (3.5 in) or full page width (7.0 in), vector PDF formats with minimum 300 DPI raster fallbacks.

---

## 2. Author and Affiliation Metadata Checklist

> [!IMPORTANT]
> The author list below is derived from the author's prior accepted publication (Paper ID 1566) and must be formally confirmed by all co-authors prior to CMT portal entry.

- [ ] **Author Confirmation Status:**
  1. **Bhagat Singh** (First / Primary Author)
     - Affiliation: Department of Computer Science and Engineering, Motilal Nehru National Institute of Technology Allahabad, Prayagraj, Uttar Pradesh, India
     - Email: `bhagat.20243073@mnnit.ac.in`
     - ORCID iD: `[Verify / Confirm]`
  2. **Dr. Namrata Singh** (Co-Author)
     - Affiliation: Amity School of Engineering and Technology, Amity University Bengaluru, Karnataka, India
     - Email: `nsingh@blr.amity.edu`
     - ORCID: `0000-0002-1294-5816`
  3. **Dr. Vikash Bhardwaj** (Co-Author)
     - Affiliation: Dewan VS Institute of Engineering & Technology, Meerut (U.P.), India
     - Email: `bhardwajvikash.ee@gmail.com`
     - ORCID: `0000-0002-6897-4309`
- [ ] **Corresponding Author Designated:** `[To be designated by co-authors]`
- [ ] **Author Order Approved:** All three co-authors have approved the author sequence.

---

## 3. Claim Integrity & Scope Non-Negotiables

- [x] **No Unsupported Claims:** 100% of numerical values grounded in commit `1040cc4`.
- [x] **Workload Framing:** Explicitly identified as 10 programmatic synthetic workloads and 17 closed-loop algorithmic attack suites across 30 random seeds (no false claims of enterprise production traces).
- [x] **Ablation Scoping:** The 93.04% credit accrual reduction is strictly scoped to ablation `rec_010`, not general traffic suppression.
- [x] **Throughput Breakdown:** Workload-specific throughput suppression reported accurately (W04 81.9%, W08 56.7%, W10 85.7%, mean 74.8%).
- [x] **Burst Capacity vs. Idle Ceiling:** Mathematically decoupled ($C_{\text{burst\_max}}(K) = B_{\max} + I_{\text{ceil}}(K)/d$).
- [x] **Pareto Reconciliation:** Scope 1 ($c \le 700$) dominance by Static Dual Bucket clearly acknowledged; Scope 2 non-dominated frontier demonstrated.
- [x] **Guarantees:** Deterministic local-time bound ($G_1$), physical-time envelope ($G_{1,\delta}$ under $|\Delta\tau| \le 10$ ms), and single-event failover over-admission bound ($G_5 \le 2,000$ tokens per key) precisely derived.
- [x] **Redis Latency & Durability:** 42 ns JMH P50, 0.259 ms async P50, and 0.348 ms `WAIT 1` P50 correctly reported. `WAIT 1` P99 (1.301 ms) is **never** referred to as sub-millisecond. Volatile replica acknowledgement explicitly distinguished from disk durability.
- [x] **Idempotency & Memory:** 5-condition contract stated; ~392 MB per million records framed as scenario hash capacity.
- [x] **No False Production Claims:** Software clearly described as an academic reference implementation and testbed prototype.

---

## 4. Distinct-Paper & Overlap Audit

- [x] **Comparison with Paper ID 1566:** Comprehensive overlap audit completed ([`PAPER_OVERLAP_AUDIT.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/submission/nice-teas-asia-2026/PAPER_OVERLAP_AUDIT.md)).
- [x] **Textual Independence:** 0% verbatim or paraphrased overlap.
- [x] **Problem Independence:** Paper 1566 tackles AI/LLM inference queueing and token prediction; DREB-RCA70 tackles cloud API rate limiting and credit farming.
- [x] **Dataset & Result Independence:** Zero shared figures, tables, CSV files, or simulator traces.

---

## 5. Artifact & Deliverables Verification

- [x] **Markdown Manuscript:** `submission/nice-teas-asia-2026/NICE_TEAS_DREB_RCA70_MANUSCRIPT.md`
- [x] **LaTeX Package:** `submission/nice-teas-asia-2026/latex/main.tex`, `IEEEtran.cls`, `references.bib`
- [x] **Compiled PDF:** `submission/nice-teas-asia-2026/latex/main.pdf`
- [x] **Visual Assets:** Sourced from `submission/nice-teas-asia-2026/figures/`
- [x] **Audits Completed:** `CLAIM_AUDIT.md`, `PAPER_OVERLAP_AUDIT.md`, `SUBMISSION_CHECKLIST.md`

---

## 6. Microsoft CMT Submission Step-by-Step Guide (for Human Authors)

1. Log into the official **IEEE NICE-TEAS ASIA 2026 Microsoft CMT Portal**.
2. Select **"Create New Submission"**.
3. **Title:** Copy exact title from Section 1.
4. **Abstract:** Copy exact abstract from manuscript (no markdown syntax, plain text).
5. **Authors:** Add Author 1, Author 2, Author 3 with exact email addresses and institutional affiliations.
6. **Subject Areas:** Select *Cloud Computing / Distributed Systems / Network Security / Performance Engineering*.
7. **Conflict of Interest (COI):** Declare institutional conflicts (MNNIT Allahabad, Amity University, Dewan VS IT).
8. **File Upload:** Upload `submission/nice-teas-asia-2026/latex/main.pdf`.
9. **Confirmation:** Review the generated CMT summary sheet and save the Submission ID.
