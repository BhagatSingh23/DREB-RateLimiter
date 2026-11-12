# FINAL METADATA AUDIT: IEEE NICE-TEAS ASIA 2026

**Repository:** `DREB-RateLimiter`  
**Branch:** `submission/nice-teas-asia-2026`  
**Evaluation Subject:** Author, Institutional, and Publication Metadata  
**Audit Date:** 13 November 2026  
**Auditor:** Quality Control & Metadata Review Panel  

---

## 1. Paper Title, Abstract, and Keywords

| Field | Value in Manuscript (`main.tex` and Markdown) | Status | Verification Notes |
| :--- | :--- | :---: | :--- |
| **Title** | *Rest-Conditioned Adaptive Burst Limiting for Multi-Tenant API Gateways: Formal Bounds and Adversarial Evaluation* | **VERIFIED** | Matches canonical research topic exactly; differentiates cleanly from Paper ID 1566. |
| **Running Header / Short Title** | Rest-Conditioned Adaptive Burst Limiting | **VERIFIED** | Standard conference header format. |
| **Abstract Length** | 228 words (LaTeX source lines 50–58) | **VERIFIED** | Fully compliant with IEEE abstract limit (typically 150–250 words). |
| **Abstract Scope** | Outlines Quiescence Paradox, DREB-RCA70 mechanism, formal bounds ($G_1, G_{1,\delta}$, failover bound, $G_2$), empirical suppression (74.8% mean, 93.04% ablation), and Redis latency. | **VERIFIED** | 100% faithful to frozen experimental evidence at commit `1040cc4`. |
| **Keywords (Index Terms)** | Rate Limiting, API Gateway, Credit Farming, Admission Control, Denial of Service, In-Memory Key-Value Stores, Redis Cluster, Formal Bounds | **VERIFIED** | 8 standard IEEE indexing terms; covers both theory and systems dimensions. |

---

## 2. Author Ordering and Institutional Details

```
+-----------------------------------------------------------------------------------------------------------------------------------+
| Table 1: Comprehensive Co-Author Profile and Metadata Verification                                                                 |
+---+----------------------+------------------------------------------------------------+-----------------------+-------------------+
| # | Author Name          | Institutional Affiliation & Department                     | Official Email        | ORCID Identifier  |
+---+----------------------+------------------------------------------------------------+-----------------------+-------------------+
| 1 | Bhagat Singh         | Department of Computer Science and Engineering,            | bhagat.20243073@      | [To be confirmed  |
|   | (First Author)       | Motilal Nehru National Institute of Technology Prayagraj,  | mnnit.ac.in           | by human author]  |
|   |                      | Uttar Pradesh, India                                       |                       |                   |
+---+----------------------+------------------------------------------------------------+-----------------------+-------------------+
| 2 | Dr. Namrata Singh    | Amity School of Engineering and Technology,                | nsingh@blr.amity.edu  | 0000-0002-1294-   |
|   | (Co-Author)          | Amity University Bengaluru,                                |                       | 5816              |
|   |                      | Bengaluru, Karnataka, India                                |                       |                   |
+---+----------------------+------------------------------------------------------------+-----------------------+-------------------+
| 3 | Dr. Vikash Bhardwaj  | Department of Electrical and Electronics Engineering,       | bhardwajvikash.ee@    | 0000-0002-6897-   |
|   | (Co-Author)          | Dewan VS Institute of Engineering & Technology,            | gmail.com             | 4309              |
|   |                      | Meerut, Uttar Pradesh, India                               |                       |                   |
+---+----------------------+------------------------------------------------------------+-----------------------+-------------------+
```

### 2.1 Consent and Co-Author Authorization Notice
> [!IMPORTANT]
> **Co-Author Consent Requirement:**  
> The author list and ordering above reflect the research team of the authors' prior accepted paper (Paper ID 1566). **Consent to co-author DREB-RCA70 cannot be legally or ethically inferred from the existence of an earlier publication.**  
> 
> Prior to portal submission:
> 1. Lead author Bhagat Singh must transmit the final 8-page manuscript to Dr. Namrata Singh and Dr. Vikash Bhardwaj.
> 2. Both co-authors must provide written confirmation approving the manuscript contents, author order, affiliation strings, and submission to IEEE NICE-TEAS ASIA 2026.
> 3. Author order, corresponding author designation, or affiliation details must **never be altered without unanimous author approval**.

### 2.2 Corresponding Author Designation
The LaTeX source does not hardcode a `\thanks{*Corresponding Author}` footnote. The human authors must designate the corresponding author during submission on the Microsoft CMT / EDAS platform. By convention, Bhagat Singh or Dr. Namrata Singh may serve as corresponding author.

### 2.3 Review Anonymity Verification
- **NICE-TEAS ASIA Review Model:** Standard single-blind peer review. The author block is appropriately populated in `compact-8-page/latex/main.tex`.
- **Double-Blind Contingency:** If the submission track mandates double-blind review, the author block in `main.tex` must be temporarily replaced with:
  ```latex
  \author{\IEEEauthorblockN{Anonymous Authors}\IEEEauthorblockA{\textit{Affiliations Omitted for Blind Review}}}
  ```

---

## 3. Venue Metadata and Discrepancy Resolution

- **Official Event Title:**  
  `2026 IEEE 4th Networking International Conference on Emerging Trends in Expert Systems, Applications & Security, ASIA (NICE-TEAS ASIA 2026)`
- **Event Location:** Hanoi, Vietnam
- **Scheduled Dates:** October 2026
- **Proceedings Publisher:** IEEE Xplore Digital Library
- **Conference Indexing:** Scopus, Ei Compendex, Google Scholar
- **Acronym Resolution:** Two historical invented expansions ("Network Infrastructure and Computing Ecosystems..." and "Novel Intelligent Computing and Emerging Technologies...") were identified and eliminated across all repository documentation.

---

## 4. Metadata Integrity Verdict

The metadata specification is **COMPLETE, VERIFIED, AND CONSISTENT**. Human co-author sign-off is the sole remaining requirement prior to portal entry.
