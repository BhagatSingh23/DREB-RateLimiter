# Pre-Submission Checklist: IEEE NICE-TEAS ASIA 2026 (Compact 8-Page Edition)

**Conference:** 4th IEEE International Conference on Network Infrastructure and Computing Ecosystems: Theory, Experiments, Architecture, and Systems (NICE-TEAS ASIA 2026)  
**Location / Dates:** Hanoi, Vietnam | October 2026  
**Document Evaluated:** `submission/nice-teas-asia-2026/compact-8-page/main.pdf`  
**LaTeX Source:** `submission/nice-teas-asia-2026/compact-8-page/latex/main.tex`  
**Canonical Research Freeze:** Commit `1040cc4` (`research/systematic-improvement`)  

---

## 1. Compliance Audit

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
| **Mathematical Bounds** | Formal contract ($G_1, G_{1,\delta}, G_2, G_3, G_4$) | **PASS** | Theorems 1–5, Corollary 1, and Lemma 1 retained with exact mathematical equations and proofs. |
| **Empirical Claims** | Verified against frozen data | **PASS** | 0.00% FRR, 74.8% mean farming suppression, 93.04% ablation accrual reduction, 42 ns in-memory, 0.259 ms async RTT, 0.348 ms sync `WAIT 1`. |
| **Bibliography** | IEEE citation style | **PASS** | 10 standard references (`references.bib`) compiled cleanly with BibTeX. |

---

## 2. Dual-Manuscript Strategy Guide for Authors

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

## 3. Pre-Upload Final Actions for Authors

1. **Verify IEEE PDF eXpress Compliance:**  
   Upload either `main.pdf` or `compact-8-page/main.pdf` to the conference's IEEE PDF eXpress portal using Conference ID `NICETEAS2026` (or as provided in the acceptance notification). Both PDFs are compiled with Type 1 embedded fonts via Tectonic.
2. **Author Registration:**  
   Ensure that corresponding author Bhagat Singh or co-authors register on the conference CMT/EDAS portal before the manuscript deadline.
3. **Double-Blind vs. Single-Blind Verification:**  
   NICE-TEAS ASIA uses standard single-blind peer review; author block is fully populated. If organizers mandate double-blind, replace the author block in `main.tex` with `\author{\IEEEauthorblockN{Anonymous Authors}}`.
4. **Git Freeze:**  
   Commit hash `5d8bfad` and the newly generated Phase 11 commit provide immutable provenance back to the canonical research freeze `1040cc4`.
