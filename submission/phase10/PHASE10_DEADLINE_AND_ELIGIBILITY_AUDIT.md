# PHASE 10: DEADLINE AND SUBMISSION ELIGIBILITY AUDIT

**Repository:** `DREB-RateLimiter`  
**Branch:** `submission/phase10-preparation`  
**Reference Commit:** `1040cc4`  
**Current Date:** 12 November 2026  
**Auditor:** Publication Preparation Review Panel  

---

## 1. Executive Summary

This audit evaluates the immediate and future submission eligibility of the DREB-RCA70 manuscript across ACM SoCC, ACM/SPEC ICPE 2027, and IEEE/ACM ToN based on confirmed official dates as of **12 November 2026**.

### Critical Eligibility Findings:
1. **ACM SoCC 2026 is CLOSED:** The 2026 edition in Singapore (Nov 18–20, 2026) concluded its submission cycle in July 2026. **ACM SoCC 2027 has not yet been announced**; no CFP, deadlines, or submission portal exist. It cannot be targeted for immediate submission.
2. **ACM/SPEC ICPE 2027 Research Track Abstract Deadline has PASSED:** The abstract registration deadline was **Monday, November 9, 2026 (AoE)**, three days prior to the current audit date. The full paper deadline is **Monday, November 16, 2026 (AoE)**.
   - In standard HotCRP configurations, passing the abstract registration deadline closes the portal to new submission IDs.
   - Unless an author registered an abstract prior to November 9, immediate submission on November 16 is **UNVERIFIED / BLOCKED** at the system level.
   - The authors must not assume the full paper deadline overrides the abstract registration requirement.
3. **IEEE/ACM Transactions on Networking (ToN) is ROLLING:** ToN accepts submissions continuously year-round without conference-cycle deadlines. Submission is immediately eligible from an administrative standpoint.

---

## 2. Comprehensive Venue Deadline Matrix

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: Official Submission Deadlines and Administrative Eligibility Status (as of 12 Nov 2026).       |
+----------------------+--------------------+--------------------+-------------------+--------------------+
| Venue                | Abstract Deadline  | Full Paper Deadline| Notification Date | Eligibility Status |
+----------------------+--------------------+--------------------+-------------------+--------------------+
| **ACM SoCC 2026**    | May / July 2026    | July 2026          | September 2026    | **CLOSED**         |
| **ACM SoCC 2027**    | Not Announced      | Not Announced      | Not Announced     | **FUTURE CYCLE**   |
| **ACM ICPE 2027**    | 09 November 2026   | 16 November 2026   | 25 January 2027   | **UNVERIFIED /     |
| (Research Track)**   | (EXPIRED, AoE)     | (4 days remaining) | (AoE)             | **POTENTIALLY LATE**|
| **ACM ICPE 2027**    | None               | 01 February 2027   | 05 March 2027     | **OPEN**           |
| (Emerging Track)**   |                    |                    |                   |                    |
| **IEEE/ACM ToN**     | None (Rolling)     | Open Year-Round    | ~90–120 days      | **OPEN / ELIGIBLE**|
+----------------------+--------------------+--------------------+-------------------+--------------------+
```

---

## 3. Deep-Dive Audit: ACM/SPEC ICPE 2027 Abstract Deadline Issue

### 3.1 The Abstract Registration Mandate
The official ICPE 2027 Call for Contributions specifies two distinct dates for the Research Paper Track:
- **Abstract Submission Deadline:** Monday, November 9, 2026 (AoE)
- **Paper Submission Deadline:** Monday, November 16, 2026 (AoE)

In academic peer review (specifically HotCRP platforms used by ACM/SPEC ICPE), this two-phase structure serves a vital operational purpose:
1. It locks the submission pool so program chairs and PC members can perform conflict-of-interest (COI) declarations and bidding during the 7-day interval between abstract and paper deadlines.
2. HotCRP typically disables the "New Submission" button upon passing the abstract deadline, allowing only existing registered submissions (holding assigned Paper IDs) to upload PDF updates until November 16.

### 3.2 Investigation of Potential Exceptions
- **Official Policy Check:** A thorough audit of `https://icpe2027.spec.org/tracks-and-submissions/research-paper-track/` reveals **no published late-registration exception policy**.
- **HotCRP Portal State:** Because Antigravity is strictly forbidden from accessing submission portals, creating accounts, or submitting data, the actual state of the HotCRP portal (whether late registration remains open or locked) cannot be confirmed by the agent.
- **Classification:** Administrative eligibility for the ICPE 2027 Research Track is officially classified as **UNVERIFIED (HIGH RISK OF REJECTION)**.

### 3.3 Author Action Required for ICPE 2027 Research Track
If the human co-authors wish to submit DREB-RCA70 to ICPE 2027 Research Track on November 16, the authors must:
1. Independently log into the ICPE 2027 HotCRP submission portal to check if the system allows creating a new submission.
2. If the portal is locked, email the ICPE 2027 Program Chairs (`pc-chairs@icpe2027.spec.org`) immediately to request a formal late-abstract registration exception.
3. If no exception is granted, **do not attempt to submit**. Pivot to ICPE 2027 Emerging Research Track (February 1, 2027) or ACM SoCC 2027.

---

## 4. Deep-Dive Audit: ACM SoCC 2027 Timeline Feasibility

- **Historical Timing:** ACM SoCC historically operates on an annual cycle with submissions in June/July and the conference held in October/November.
- **SoCC 2026 Reference:** The 2026 conference is occurring November 18–20, 2026 in Singapore.
- **Expected SoCC 2027 Schedule:**
  - Call for Papers release: February / March 2027.
  - Abstract registration: Late June / Early July 2027.
  - Full paper deadline: Early / Mid July 2027.
  - Conference dates: October / November 2027.
- **Administrative Feasibility:** SoCC 2027 is a completely feasible future target. It provides approximately 7–8 months for human co-author review, potential implementation of an experimental API gateway integration, and formal preparation of Artifact Evaluation badges.

---

## 5. Deep-Dive Audit: IEEE/ACM ToN Rolling Submission

- **Submission Cadence:** IEEE/ACM ToN does not operate on deadlines. Authors submit whenever the manuscript is finalized via ScholarOne.
- **Review Latency:** First-round decisions typically take 3 to 5 months (90–120 days).
- **Administrative Feasibility:** Immediately accessible. However, as evaluated in `PHASE10_MANUSCRIPT_FIT_ANALYSIS.md`, submission requires structural conversion to IEEE 2-column format, addition of author ORCID iDs, and careful framing of the networking contribution.
