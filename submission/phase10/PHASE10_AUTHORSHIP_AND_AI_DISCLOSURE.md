# PHASE 10: AUTHORSHIP VERIFICATION AND AI-USE DISCLOSURE

**Repository:** `DREB-RateLimiter`  
**Branch:** `submission/phase10-preparation`  
**Reference Commit:** `1040cc4`  
**Auditor:** Publication Preparation Review Panel  

---

## 1. Executive Summary

This document establishes the formal authorship verification checklist and generative AI disclosure statements required by ACM and IEEE publication ethics policies.

In strict compliance with Phase 10 guidelines:
- **No authors, affiliations, or ORCID IDs are fabricated or assumed.** All author fields remain explicit placeholders for human author confirmation.
- **AI tools are NOT listed as authors.** Both ACM and IEEE explicitly prohibit listing artificial intelligence tools, chatbots, or agentic frameworks as co-authors.
- **Transparent AI-Use Disclosure:** Clear, standard disclosure statements are formulated for ACM and IEEE submission systems detailing the assistance of AI in codebase research, simulation scripting, and manuscript drafting.

---

## 2. Author Responsibility and Verification Checklist

The human authors must verify and sign off on each item prior to submission:

### [ ] 1. Intellectual Contribution & Author Eligibility
- Every listed author has made a substantial intellectual contribution to the research design, algorithmic formulation, experimental validation, or manuscript synthesis.
- No "honorary", "gift", or "guest" authors are included.
- All contributors who meet authorship criteria are included.

### [ ] 2. Author Order & Corresponding Author
- Author order reflects the mutual agreement of all co-authors based on relative contributions.
- One corresponding author is designated for administrative communications, proofs, and inquiries.

### [ ] 3. Institutional Affiliations & Identifiers
- Complete, accurate academic, corporate, or independent affiliations are provided for all authors.
- Verified **ORCID iDs** are confirmed for all authors (mandatory for IEEE, strongly encouraged for ACM).

### [ ] 4. Funding & Acknowledgments
- All grant support, institutional funding, compute credit allocations, or cloud testbed sponsorships are documented.
- Individuals who contributed feedback or infrastructure assistance without qualifying for authorship are acknowledged.

### [ ] 5. Conflicts of Interest (COI)
- Program committee (PC) members or editors with whom authors have institutional, advising, or recent collaborative relationships are declared in the submission portal.

### [ ] 6. Prior Publication & Simultaneous Submission
- The manuscript represents original, unpublished work.
- The manuscript is not currently under review at any other peer-reviewed conference, journal, or workshop.
- If an earlier short paper, tech report, or arXiv preprint exists, it is cited and differentiated in accordance with the target venue's dual-submission policy.

---

## 3. Official Venue Generative AI Policies

### 3.1 ACM Policy on Generative AI
Under the official **ACM Policy on Authorship and Peer Review** (accessed November 2026):
1. **Human Authorship Requirement:** "All authors of an ACM publication must be human beings who meet the criteria for authorship. Generative AI systems cannot be authors."
2. **AI Used in the Conduct of Research:** If generative AI tools were utilized to design methodology, generate code, run simulations, or create figures directly tied to research findings, the specific tools and scope of usage must be described in the **Methodology section** or **Acknowledgments**.
3. **Absolute Author Accountability:** Human authors bear 100% legal, ethical, and scientific accountability for all content, including detecting any hallucinated citations, erroneous proofs, or plagiarized text.

### 3.2 IEEE Policy on Generative AI
Under the **IEEE Author Center Policy on AI-Generated Text**:
1. **Disclosure Mandate:** The use of generative AI (including text, image generation, or code generation) must be disclosed in the **Acknowledgments** section.
2. **Disclosure Elements:** The disclosure must specify the name of the AI system, the version, the date accessed, the prompt themes, and the specific sections of the paper where AI content was incorporated.
3. **Prohibition:** AI cannot be credited as an author or co-author.

---

## 4. Standardized AI-Use Disclosure Statements

The human authors should include the applicable disclosure in the final submission:

### A. For ACM Submissions (SoCC / ICPE)
> **AI Usage Disclosure (for Methodology / Acknowledgments):**  
> *"During the preparation of this research and manuscript, the authors utilized Google Antigravity / Gemini Advanced (Anthropic / Google DeepMind agentic tools) to assist with codebase analysis, automated test harness execution, LaTeX mathematical typesetting, and drafting initial descriptive summaries. All mathematical theorems, closed-form proofs, algorithm state transitions, experimental data interpretations, and final manuscript revisions were independently verified, audited, and approved by the human authors, who take full responsibility for the integrity and accuracy of the entire work."*

### B. For IEEE Submissions (ToN / TCC)
> **Acknowledgment of Generative AI Assistance:**  
> *"The authors acknowledge the use of agentic AI assistance (Google Antigravity / Gemini 2.0) in executing automated testbed scripts and assisting in the structural drafting of manuscript sections. The human authors formulated the research questions, designed the DREB-RCA70 mechanism, verified all mathematical proofs and experimental data, and reviewed and edited all submitted text. The authors take full intellectual and scientific accountability for the content."*

---

## 5. Author Confirmation Fields (To Be Completed by Human Authors)

```
================================================================================
MANUSCRIPT: DREB-RCA70 (AdaptiveCeilingIdleCreditPolicy)
CANONICAL DRAFT: DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md
DATE: ____________________

PRIMARY AUTHOR / PI NAME:     __________________________________________________
INSTITUTIONAL AFFILIATION:    __________________________________________________
EMAIL ADDRESS:                __________________________________________________
ORCID iD:                     __________________________________________________

CO-AUTHOR 2 NAME:             __________________________________________________
INSTITUTIONAL AFFILIATION:    __________________________________________________
EMAIL ADDRESS:                __________________________________________________
ORCID iD:                     __________________________________________________

CORRESPONDING AUTHOR:         __________________________________________________
TARGET VENUE SELECTED:        [ ] ACM SoCC 2027   [ ] ACM ICPE 2027   [ ] Other
================================================================================
```
