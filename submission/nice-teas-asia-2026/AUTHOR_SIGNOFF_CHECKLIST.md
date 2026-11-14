# AUTHOR SIGNOFF & CMT SUBMISSION CHECKLIST
## IEEE NICE-TEAS ASIA 2026

**Conference:** 2026 IEEE 4th Networking International Conference on Emerging Trends in Expert Systems, Applications & Security, ASIA (NICE-TEAS ASIA 2026)  
**Location / Venue:** Hanoi, Vietnam  
**Conference Date:** October 2026  
**Proceedings Publisher:** IEEE Xplore Digital Library (Indexed by Scopus, Ei Compendex, Web of Science, Google Scholar)  
**Submission Platform:** Microsoft CMT (Conference Management Toolkit)  
**Repository:** `BhagatSingh23/DREB-RateLimiter`  
**Branch:** `submission/nice-teas-asia-2026`  
**Audit Base Commit:** `ff6aed5`  
**Preparation Date:** 14 November 2026  
**Auditor / Tool:** Antigravity Autonomous Audit Harness (Phase 14 Final Signoff)  

---

> [!IMPORTANT]
> ### NOTICE TO HUMAN AUTHORS & CORRESPONDING AUTHOR
> This document is the final pre-submission authorization checklist.
> - **AI agents cannot submit papers, accept legal declarations, or infer human consent.**
> - All empirical and theoretical claims, LaTeX sources, compiled PDFs, and formatting requirements in this repository have been independently compiled, cross-verified, and certified.
> - **The actions below must be reviewed, confirmed, and executed exclusively by the human authors.**

---

## 1. Final Paper Title

```text
Rest-Conditioned Adaptive Burst Limiting for Multi-Tenant API Gateways: Formal Bounds and Adversarial Evaluation
```

- **Short Running Header:** *Rest-Conditioned Adaptive Burst Limiting*
- **Verification Status:** **VERIFIED**
  - Matches canonical research subject in `PHASE9_2_CLAIM_FREEZE_MANIFEST.json`.
  - 100% distinct from author group's prior accepted publication (*Paper ID 1566*, which addresses LLM token prediction).
  - Identical in both 8-page and 10-page editions.

---

## 2. Abstract & Keywords

### 2.1 Primary Edition (Compact 8-Page) Abstract
- **Rendered Word Count:** 335 words
- **Source LaTeX Word Count:** 435 words (including inline mathematical notation)
- **Plain-Text Portal Form Version (Copy & Paste for CMT):**

```text
Multi-tenant cloud API gateways face an operational tension between accommodating legitimate quiescent client bursts and mitigating low-rate periodic abuse. Conventional rate limiters—such as Token Bucket, Leaky Bucket, and Generic Cell Rate Algorithm (GCRA)—either enforce rigid burst caps that trigger severe False Rejection Rates (up to 30–100%) during benign batch spikes, or permit wide burst allowances that adversaries systematically exploit via credit farming. In credit farming, an attacker sleeps to accumulate burst allowances, discharges destructive micro-bursts that saturate downstream resources, and repeats below sustained rate thresholds.

This paper presents DREB-RCA70 (Dual-Rate Elastic Burst with Rest-Conditioned Adaptive Ceiling), an admission-control mechanism and calibrated policy resolving this trade-off. DREB-RCA70 decouples baseline liquid capacity (B_max = 700) from an auxiliary idle credit pool (I), penalizes deficit spending via an asymmetric ratio (d = 1.5), and dynamically contracts the allowable idle ceiling via an abuse score K(t) coupled with continuous linear recovery (lambda_rec = 0.05 s^-1): I_ceil(K) = baseCap / (1.0 + gamma * K), with baseCap = 450.0 and gamma = 0.75. Total burst admission capacity scales as C_burst_max(K) = B_max + I_ceil(K)/d, granting 1,000 tokens to quiescent clients while throttling repeat attackers.

We prove deterministic rate bounds under local server time (G1: A(T) <= 1285.7143 + 1000.0 * T), derive the conditional physical-time rate envelope (G1_delta: A(t) <= 1295.7143 + 1000.0 * t) under bounded clock skew |Delta_tau| <= 10 ms, and prove that single-event distributed failover over-admission is strictly bounded by 2 * C_max = 2,000 tokens per client key. Evaluated against 10 synthetic workloads and 17 closed-loop attack families across 30 random seeds, DREB-RCA70 suppresses farming throughput by 56.7% to 85.7% relative to a large token bucket (averaging 74.8% across evaluated attacks) and reduces credit accrual rate by 93.04% in parameter ablation, while preserving 0.00% false rejection for legitimate 1,000-token batch bursts under quiescent conditions. On a 6-node Redis Cluster, DREB-RCA70 achieves 42 ns in-memory dispatch latency (P50), 0.259 ms asynchronous round-trip latency (P50), and 0.348 ms synchronous WAIT 1 latency (P50), establishing a resilient foundation for line-rate cloud admission control.
```

### 2.2 Full Edition (10-Page) Abstract
- **Rendered Word Count:** 377 words
- **Source LaTeX Word Count:** 482 words
- **Verification Status:** Fully consistent with the 8-page abstract, incorporating extended description of downstream microservice degradation and P99 latency percentiles (0.667 ms async, 1.301 ms sync `WAIT 1`).

### 2.3 Official Keywords (Index Terms)
```text
Rate Limiting, API Gateway, Credit Farming, Admission Control, Denial of Service, In-Memory Key-Value Stores, Redis Cluster, Formal Bounds
```
- **Total Keywords:** 8 terms
- **Coverage:** Systems architecture, cybersecurity/threat mitigation, theoretical queueing bounds, and distributed performance engineering.

---

## 3. Author Ordering, Affiliations, and Contact Details

> [!CAUTION]
> **Co-Author Consent Requirement:** Consent to co-author DREB-RCA70 **cannot be legally or ethically inferred** from past co-authorship on Paper ID 1566. Lead author Bhagat Singh must obtain explicit written confirmation from Dr. Namrata Singh and Dr. Vikash Bhardwaj approving the author ordering, institutional affiliations, and manuscript submission.

```
+---+----------------------+------------------------------------------------------------+-----------------------------+-----------------------+
| # | Author Name          | Institutional Affiliation & Department                     | Official Email Address      | ORCID Identifier      |
+---+----------------------+------------------------------------------------------------+-----------------------------+-----------------------+
| 1 | Bhagat Singh         | Department of Computer Science and Engineering,            | bhagat.20243073@mnnit.ac.in | [Human Author to      |
|   | (Primary / Student)  | Motilal Nehru National Institute of Technology Allahabad,  |                             | Confirm / Provide]    |
|   |                      | Prayagraj, Uttar Pradesh 211004, India                     |                             |                       |
+---+----------------------+------------------------------------------------------------+-----------------------------+-----------------------+
| 2 | Dr. Namrata Singh    | Amity School of Engineering and Technology,                | nsingh@blr.amity.edu        | 0000-0002-1294-5816   |
|   | (Co-Author)          | Amity University Bengaluru,                                |                             |                       |
|   |                      | Bengaluru, Karnataka 562110, India                         |                             |                       |
+---+----------------------+------------------------------------------------------------+-----------------------------+-----------------------+
| 3 | Dr. Vikash Bhardwaj  | Department of Electrical and Electronics Engineering,       | bhardwajvikash.ee@gmail.com | 0000-0002-6897-4309   |
|   | (Co-Author)          | Dewan VS Institute of Engineering & Technology,            |                             |                       |
|   |                      | Meerut, Uttar Pradesh 250103, India                        |                             |                       |
+---+----------------------+------------------------------------------------------------+-----------------------------+-----------------------+
```

### Institutional Conflict of Interest (COI) Declarations for CMT:
When creating the submission in Microsoft CMT, declare institutional conflicts for:
1. Motilal Nehru National Institute of Technology (MNNIT) Allahabad
2. Amity University Bengaluru / Amity Education Group
3. Dewan VS Institute of Engineering & Technology (DVSIET) Meerut / Dr. A.P.J. Abdul Kalam Technical University

---

## 4. Corresponding Author Nomination

- [ ] **Author Action Required:** Formally designate the Corresponding Author in the CMT submission portal.
  - **Option A (Standard Student Submission):** Bhagat Singh (`bhagat.20243073@mnnit.ac.in`)
  - **Option B (Senior / Faculty Supervisor):** Dr. Namrata Singh (`nsingh@blr.amity.edu`)
- **Note:** The LaTeX source deliberately avoids hardcoding a footnote to allow flexibility during CMT portal setup according to institutional preferences.

---

## 5. Manuscript Editions & Selection Strategy

Both editions have been compiled, verified, and audited against the frozen research baseline:

```
+--------------------------+-------------------------------------+-------------------------------------+
| Dimension                | Primary Edition (Recommended)       | Full Extended Edition               |
+--------------------------+-------------------------------------+-------------------------------------+
| Directory Path           | submission/nice-teas-asia-2026/     | submission/nice-teas-asia-2026/     |
|                          | compact-8-page/                     |                                     |
| PDF File Path            | compact-8-page/main.pdf             | main.pdf (also latex/main.pdf)      |
| Exact Printed Pages      | Exactly 8 Pages                     | Exactly 10 Pages                    |
| PDF File Size            | 252,434 bytes                       | 285,825 bytes                       |
| SHA-256 Checksum         | 4a4d32cb2bec6ec574dd0cfbe5c9a93a152 | 2854f7b1c7e49f0c4cb4e301de4c099a701 |
|                          | 40d0fb31d9446d3190520f9ba6976       | 426e6396324c86080ec493bce4b66       |
| LaTeX Source             | compact-8-page/latex/main.tex       | latex/main.tex                      |
| Embedded Figures         | 5 native vector figures (1,2,3,5,6) | 6 native vector figures (1,2,3,4,5,6)|
| Tables                   | 5 publication tables (I–V)          | 6 publication tables (I–VI)         |
| Overlength Risk          | 0% (Standard IEEE 8-page budget)    | Requires overlength page fee if CFP |
|                          |                                     | enforces 8-page standard ceiling    |
| Recommended Use Case     | DEFAULT CMT SUBMISSION ASSET        | FALLBACK if CFP requires 10 pages   |
+--------------------------+-------------------------------------+-------------------------------------+
```

### Recommendation:
> [!TIP]
> **Submit `compact-8-page/main.pdf` as the primary manuscript.**  
> It conveys 100% of the mathematical guarantee contract ($G_1, G_{1,\delta}, G_2, G_3$, failover bound), all empirical benchmarks (10 workloads, 17 closed-loop attacks, 30 seeds), Redis latency ladder, and Pareto frontiers within the strict 8-page limit, avoiding overlength fees or desk-reject risks.

---

## 6. Submission Category & Conference Track

- **Conference Track:** Track 1: *Advanced Networking & Distributed Systems* / *Network Security & Cloud Infrastructure*
- **Paper Type:** Regular Research Paper
- **Review Mode:** Single-Blind (Author names, affiliations, and emails are fully shown on Page 1)
  - *Contingency Note:* If CMT instructions specifically mandate Double-Blind review for this track, replace the author block in `main.tex` with `\author{\IEEEauthorblockN{Anonymous Authors}}` and recompile using `tectonic`.

---

## 7. Mathematical & Experimental Contract Verification

The following theorems, lemmas, corollaries, and empirical numbers have been verified between `PHASE9_2_CLAIM_FREEZE_MANIFEST.json` and the manuscript:

| Claim / Parameter | Frozen Value in Manifest | Value in 8-Page Manuscript | Verification Status |
| :--- | :--- | :--- | :---: |
| **Calibrated Policy (RCA70)** | $R=1000, B_{\max}=700, \text{baseCap}=450, d=1.5, \gamma=0.75, \lambda_{\text{rec}}=0.05$ | Identical ($R=1000, B=700, I=450, d=1.5, \gamma=0.75$) | **VERIFIED** |
| **Theorem 1 ($G_1$)** | $A(T) \le 1285.7143 + 1000.0 \cdot T$ | Eq. (5): $A(T) \le 1285.7143 + 1000.0 \cdot T$ | **VERIFIED** |
| **Theorem 2 ($G_{1,\delta}$)** | $A(\Delta t) \le S_{\max} + R(\Delta t + 2\delta)$ | Eq. (6): $A(\Delta t) \le (S_{\max} + 2R\delta) + R\Delta t$ | **VERIFIED** |
| **Corollary 1** | $A(\Delta t) \le 1295.7143 + 1000.0 \cdot \Delta t$ under $|\Delta\tau| \le 10$ ms | Eq. (7): $A(\Delta t) \le 1295.7143 + 1000.0 \cdot \Delta t$ | **VERIFIED** |
| **Theorem 3 (Failover)** | $A_{\text{event\_burst}} \le 2 \times C_{\max} = 2,000.0$ tokens | Eq. (8): $A_{\text{event\_burst}} \le 2 \times C_{\max} = 2000$ | **VERIFIED** |
| **Theorem 4 ($G_2$ Sufficiency/Necessity)** | $\lim \frac{N_E(T)}{T} = 0 \implies \lim \frac{A(T)}{T} \le R$ (Universal Sufficiency); necessity holds under saturated adversarial demand ($c_{\min} > R \cdot \Delta t_{\text{event}}$) | Eq. (9): Formally proven under explicit workload assumptions (Phase 15 Audit) | **VERIFIED UNDER EXPLICIT ASSUMPTIONS** |
| **Theorem 5 ($G_3$ Recovery)** | $\tau(1.0 \to 0.05) = 19.0$ s, $\tau_0 = 20.0$ s | Eq. (10): $19.0$ s and $20.0$ s | **VERIFIED** |
| **Benign FRR** | 0.00% False Rejection at 1,000 tokens | Table II: 0.00% under quiescent state | **VERIFIED** |
| **Adversarial Farming (W04)** | 81.92% suppression ($0.4517 \to 0.0817$) | Table III: 81.9% ($0.4517 \to 0.0817$) | **VERIFIED** |
| **Adversarial Farming (W08)** | 56.67% suppression ($0.8000 \to 0.3467$) | Table III: 56.7% ($0.8000 \to 0.3467$) | **VERIFIED** |
| **Adversarial Farming (W10)** | 85.71% suppression ($0.3500 \to 0.0500$) | Table III: 85.7% ($0.3500 \to 0.0500$) | **VERIFIED** |
| **Closed-Loop Ablation** | 93.04% bonus credit rate suppression | Table III: 93.04% ($1.0000 \to 0.0696$) | **VERIFIED** |
| **In-Memory Java Latency** | P50: 42 ns, P90: 61 ns, P99: 83 ns | Table IV: 42 ns / 61 ns / 83 ns (0 B/op) | **VERIFIED** |
| **Redis Lua Engine Latency** | P50: 0.082 ms, P90: 0.145 ms, P99: 0.310 ms | Table IV: 0.082 ms / 0.145 ms / 0.310 ms | **VERIFIED** |
| **Async Network RTT Latency** | P50: 0.259 ms, P90: 0.412 ms, P99: 0.667 ms | Table IV: 0.259 ms / 0.412 ms / 0.667 ms | **VERIFIED** |
| **Sync `WAIT 1` Latency** | P50: 0.348 ms, P90: 0.720 ms, P99: 1.301 ms | Table IV: 0.348 ms / 0.720 ms / 1.301 ms | **VERIFIED** |
| **Memory Footprint** | Java: 150.6–165.3 MB/M; Redis: 392 MB/M | Section VI: $216 N_{\text{state}} + 176 N_{\text{idemp}}$ | **VERIFIED** |

---

## 8. Required IEEE Author Declarations

Before submitting on Microsoft CMT, the corresponding author must affirm the following statements:

- [ ] **Originality & Prior Publication:** The manuscript represents original work that has not been published previously, is not under consideration for publication elsewhere, and will not be submitted elsewhere while under review at IEEE NICE-TEAS ASIA 2026.
- [ ] **Distinctness from Paper ID 1566:** This manuscript is completely independent of the authors' accepted paper (Paper ID 1566: *Deterministic Latency and Memory Guarantees for AI Inference Pipelines*). There is 0% verbatim or structural overlap.
- [ ] **Author Consent:** All listed authors have reviewed the final manuscript, agreed to its submission to IEEE NICE-TEAS ASIA 2026, approved the author order and affiliations, and consented to be held accountable for its contents.
- [ ] **IEEE Plagiarism & Dual Submission Policy:** The authors acknowledge and accept the IEEE Policy on Plagiarism and Multiple Submissions.
- [ ] **Financial & Presentation Commitment:** If accepted, at least one author will register at the full conference author registration rate and present the paper at NICE-TEAS ASIA 2026 in Hanoi, Vietnam, in accordance with IEEE conference presentation policies.

---

## 9. Presentation & Registration Rules

- **Venue:** Hanoi, Vietnam (In-person presentation; verify with organizers if hybrid/virtual option is permitted).
- **Author Registration Deadline:** Follows camera-ready acceptance notification.
- **Publication Requirement:** IEEE reserves the right to exclude an accepted paper from IEEE Xplore if the paper is not presented at the conference ("No-Show Policy").
- **IEEE PDF eXpress Validation:** Before uploading final camera-ready versions, the PDF must be validated through the official IEEE PDF eXpress portal using the assigned conference code. (Note: The repository PDF already embeds 100% Type 1 / Type 0 subsetted fonts and uses standard Letter paper dimensions).

---

## 10. Outstanding Author Confirmations Checklist

The following items **can only be confirmed and completed by the human authors**:

| # | Item to Confirm | Responsibility | Status |
| :-: | :--- | :--- | :---: |
| 1 | Written sign-off from Dr. Namrata Singh on manuscript and author order | Bhagat Singh | [ ] Pending |
| 2 | Written sign-off from Dr. Vikash Bhardwaj on manuscript and author order | Bhagat Singh | [ ] Pending |
| 3 | Confirmation of Bhagat Singh's ORCID iD (add to CMT profile) | Bhagat Singh | [ ] Pending |
| 4 | Selection between 8-Page (`compact-8-page/main.pdf`) and 10-Page (`main.pdf`) | All Co-Authors | [ ] Pending |
| 5 | Designation of Corresponding Author on CMT portal | All Co-Authors | [ ] Pending |
| 6 | Creation of Microsoft CMT account using official institutional emails | Corresponding Author | [ ] Pending |
| 7 | Entry of Conflict of Interest (COI) institutions in CMT | Corresponding Author | [ ] Pending |
| 8 | Final PDF upload and confirmation of CMT Submission ID | Corresponding Author | [ ] Pending |

---

## 11. Final Upload Execution Guide for Human Author

Follow these sequential steps on submission day:

1. **Obtain Co-Author Approval:** Ensure Dr. Namrata Singh and Dr. Vikash Bhardwaj have confirmed the final PDF via email.
2. **Access Microsoft CMT:** Log into the official **NICE-TEAS ASIA 2026 CMT portal**.
3. **Initiate New Submission:** Click **"Create New Submission"**.
4. **Enter Title:** Copy and paste the exact title from Section 1.
5. **Enter Abstract:** Copy and paste the plain-text abstract from Section 2.1 into the abstract text area.
6. **Add Authors:**
   - Author 1: Bhagat Singh (`bhagat.20243073@mnnit.ac.in`), MNNIT Allahabad, India.
   - Author 2: Dr. Namrata Singh (`nsingh@blr.amity.edu`), Amity University Bengaluru, India.
   - Author 3: Dr. Vikash Bhardwaj (`bhardwajvikash.ee@gmail.com`), Dewan VS IET Meerut, India.
7. **Select Corresponding Author:** Check the corresponding author checkbox.
8. **Select Primary Subject Track:** Choose *Advanced Networking / Distributed Systems / Cloud Infrastructure*.
9. **Declare Institutional COIs:** Enter MNNIT Allahabad, Amity University, Dewan VS IET.
10. **Upload PDF:**
    - Primary recommended file: `submission/nice-teas-asia-2026/compact-8-page/main.pdf`
    - (Ensure filename conforms to CMT instructions, e.g., `DREB_RCA70_NICE_TEAS_2026.pdf`).
11. **Submit & Record:** Click **"Submit"**, download the confirmation receipt, and record your Submission ID.

---

### Audit Signoff Certification
```
Artifact Audit Status: 100% VERIFIED AND READY
Mathematical Bounds:   FORMALLY DERIVED AND CODE-VERIFIED (DrebRcaPropertiesTest PASS)
Empirical Benchmarks:  FROZEN AND GROUNDED (Commit 1040cc4)
PDF Compilation:       INDEPENDENT COMPILATION IDENTICAL (Tectonic 0.17.0)
Action Required:       HUMAN AUTHOR SIGNOFF AND PORTAL UPLOAD
```
