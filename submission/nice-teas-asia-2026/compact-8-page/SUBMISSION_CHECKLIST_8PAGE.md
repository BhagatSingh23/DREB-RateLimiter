# Pre-Submission Checklist: IEEE NICE-TEAS ASIA 2026 (Compact 8-Page Edition)

**Conference:** 2026 IEEE 4th Networking International Conference on Emerging Trends in Expert Systems, Applications & Security, ASIA (NICE-TEAS ASIA 2026)  
**Location / Dates:** Hanoi, Vietnam | October 2026  
**Document Evaluated:** `submission/nice-teas-asia-2026/compact-8-page/main.pdf`  
**LaTeX Source:** `submission/nice-teas-asia-2026/compact-8-page/latex/main.tex`  
**Canonical Research Freeze:** Commit `1040cc4` (`research/systematic-improvement`)  
**Phase 12 Verification Date:** 13 November 2026  
**Final Verdict:** **READY FOR HUMAN AUTHOR APPROVAL**  

---

## 1. Compliance and Guarantee Audit

| Requirement | Specification | Status | Verification Notes |
| :--- | :--- | :---: | :--- |
| **Page Count** | Exactly 8 pages | **PASS** | Verified via `pypdf` on `compact-8-page/main.pdf`: 8 total pages. Terminated cleanly on Page 8. |
| **Document Class** | IEEEtran conference format | **PASS** | `\documentclass[conference]{IEEEtran}` (v1.8b). |
| **Font & Typography** | Standard 10pt Times, 2 columns | **PASS** | Standard IEEEtran fonts and margins; no artificial shrinking or spacing overrides. |
| **Overfull Hboxes** | Zero text/table overflow | **PASS** | Tectonic log shows 0 overfull hboxes in paragraphs or tables. All tables wrapped in `\resizebox{\columnwidth}{!}{...}`. |
| **Title & Subtitle** | Descriptive and precise | **PASS** | *"Rest-Conditioned Adaptive Burst Limiting for Multi-Tenant API Gateways: Formal Bounds and Adversarial Evaluation"* |
| **Author Information** | 3 co-authors with affiliations | **PASS** | Bhagat Singh (MNNIT Prayagraj), Dr. Namrata Singh (Amity Bengaluru, ORCID), Dr. Vikash Bhardwaj (Dewan VS IET, ORCID). |
| **Abstract Length** | Concise conference abstract | **PASS** | 228 words detailing problem, mechanism, formal bounds, empirical results, and Redis metrics. |
| **Keywords** | Indexing terms | **PASS** | 8 keywords: Rate Limiting, API Gateway, Credit Farming, Admission Control, Denial of Service, Redis Cluster, Formal Bounds. |
| **Algorithm Pseudo-code** | Standalone algorithm block | **PASS** | Algorithm 1 (*DREB-RCA70 Atomic Admission Control*) with complete line-by-line admission logic. |
| **Figures** | High-resolution vector PDF | **PASS** | 5 vector figures (Figs 1, 2, 3, 5, 6) rendered with crisp typography and clear legends. |
| **Tables** | Formatted with booktabs | **PASS** | 5 publication tables (Table 1 Benchmark Suite, Table 2 Benign Burst, Table 3 Farming, Table 4 Latency, Table 5 Pareto). |
| **Mathematical Bounds** | Canonical contract ($G_1, G_{1,\delta}, G_2, G_3$, failover) | **PASS** | Theorems 1–5, Corollary 1, and Lemma 1 retained; failover bound canonicalized without conflicting G-tags. |
| **Empirical Claims** | Verified against frozen data | **PASS** | 0.00% FRR, 74.8% mean farming suppression, 93.04% ablation accrual reduction, 42 ns in-memory, 0.259 ms async RTT, 0.348 ms sync `WAIT 1`. |
| **Font Embedding** | 100% Type 1 / Type 0 subsetted | **PASS** | Programmatically verified via `pypdf`: all 26 font descriptors embedded (`embedded: True`). |
| **Bibliography** | IEEE citation style | **PASS** | 10 standard references (`references.bib`) compiled cleanly with BibTeX. |

---

## 2. Non-Negotiable Human Author Actions

> [!IMPORTANT]
> **Co-Author Consent & Verification:**  
> AI assistants cannot infer co-author consent from prior accepted papers (e.g., Paper ID 1566). Before human submission on Microsoft CMT / EDAS:
> 1. Lead author Bhagat Singh must obtain written approval from Dr. Namrata Singh and Dr. Vikash Bhardwaj for author order, affiliations, and final manuscript text.
> 2. The corresponding author must be designated on the submission portal.
> 3. Verify conference policy regarding multiple accepted papers by the same author group within NICE-TEAS ASIA 2026.

---

## 3. Dual-Manuscript Strategy Guide for Authors

The IEEE NICE-TEAS ASIA 2026 Call for Papers contains potential ambiguity regarding page limits:
- Certain CFP notices specify *"minimum 10 pages"*.
- Other sections indicate *"up to 10 printed pages, with 8 pages for regular papers"*.

To completely safeguard the submission against desk rejections, formatting disqualification, or unexpected per-page fee overages, this repository maintains **two fully verified, publication-ready PDF manuscripts**:

```
submission/nice-teas-asia-2026/
├── main.pdf                                          <-- Authoritative 10-Page Manuscript
├── NICE_TEAS_DREB_RCA70_MANUSCRIPT.md
├── latex/
│   ├── main.tex
│   └── main.pdf
└── compact-8-page/
    ├── main.pdf                                      <-- Authoritative Exactly 8-Page Manuscript
    ├── NICE_TEAS_DREB_RCA70_COMPACT_8PAGE.md
    ├── COMPRESSION_AUDIT.md
    ├── SUBMISSION_CHECKLIST_8PAGE.md
    └── latex/
        ├── main.tex
        └── main.pdf
```

### Decision Matrix for Submission Portal:
1. **Scenario A (Portal enforces strict 8-page maximum without extra page options):**  
   Submit `compact-8-page/main.pdf`. It is certified to be exactly 8 printed pages.
2. **Scenario B (Portal enforces 10-page minimum or allows up to 10 pages without penalty):**  
   Submit `submission/nice-teas-asia-2026/main.pdf`. It is certified to be exactly 10 printed pages.
3. **Scenario C (Portal charges steep fees for pages 9 and 10):**  
   Submit `compact-8-page/main.pdf` to avoid all excess page surcharges.

---

## 4. Pre-Upload Final Actions for Authors

1. **Verify IEEE PDF eXpress Compliance:**  
   Upload `compact-8-page/main.pdf` (or `submission/nice-teas-asia-2026/main.pdf`) to the conference's IEEE PDF eXpress portal using the assigned Conference ID. All fonts are programmatically verified as Type 1 / Type 0 embedded subsets; official PDF eXpress certification requires portal login.
2. **Author Registration:**  
   Ensure that corresponding author Bhagat Singh or co-authors register on the conference CMT/EDAS portal before the manuscript deadline.
3. **Double-Blind vs. Single-Blind Verification:**  
   NICE-TEAS ASIA uses standard single-blind peer review; author block is fully populated. If organizers mandate double-blind, replace the author block in `main.tex` with `\author{\IEEEauthorblockN{Anonymous Authors}}`.
4. **Git Freeze:**  
   Commit hash `adf3b1d` and the newly generated Phase 12 commit provide immutable provenance back to the canonical research freeze `1040cc4`.
