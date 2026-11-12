# PHASE 10: SUBMISSION METADATA TEMPLATE

**Repository:** `DREB-RateLimiter`  
**Branch:** `submission/phase10-preparation`  
**Reference Commit:** `1040cc4`  
**Canonical Manuscript:** [`DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md`](file:///Users/cruise/Desktop/DREB-RateLimiter/DREB_RCA70_MANUSCRIPT_FINAL_REVIEW.md)  
**Date:** 12 November 2026  

---

## 1. Title and Abstract Metadata

### Proposed Paper Title
`DREB-RCA70: Mitigating Low-Rate Credit Farming in Cloud API Gateways via Rest-Conditioned Adaptive Idle-Credit Ceilings`

### Short Title (Running Head / Header)
`DREB-RCA70: Mitigating Low-Rate Credit Farming in Cloud API Gateways`

### Canonical Abstract (Verbatim from Frozen Manuscript `1040cc4`)
```text
Multi-tenant cloud API gateways face an operational tension between accommodating legitimate, quiescent client bursts and preventing low-rate, periodic abuse. Traditional rate limiters—such as Token Bucket, Leaky Bucket, and Generic Cell Rate Algorithm (GCRA)—either enforce rigid burst caps that trigger severe false-rejection rates (up to 30–100%) during benign batch spikes, or permit high burst allowances that adversaries systematically exploit via credit farming. In a credit-farming attack, an adversary remains intentionally quiescent to accumulate burst credits, discharges them in destructive micro-bursts that degrade downstream backend services, and repeats the cycle below sustained rate detection thresholds.

We present DREB-RCA70 (Dual-Rate Elastic Burst with Rest-Conditioned Adaptive Ceiling), an admission-control mechanism and calibrated operational policy designed to eliminate this trade-off. DREB-RCA70 decouples baseline liquid capacity (B_max = 700) from an auxiliary idle credit pool (I), governs deficit spending via an asymmetric penalty ratio (d = 1.5), and dynamically modulates the allowable idle-credit ceiling via a non-linear abuse score K(t) coupled with continuous linear recovery (\lambda_rec = 0.05 s^-1):
    I_ceil(K) = baseCap / (1.0 + \gamma * K)    (baseCap = 450.0, \gamma = 0.75)
Total instantaneous burst admission capacity scales dynamically as C_burst_max(K) = B_max + I_ceil(K)/d, granting 1,000 tokens to quiescent clients while restricting repeat attackers.

We formally prove deterministic rate bounds under local server time (G_1: A(T) <= 1285.7143 + 1000.0 * T), derive the conditional physical-time rate envelope (G_1,\delta: A(t) <= 1295.7143 + 1000.0 * t) under bounded clock skew |\Delta \tau| <= 10 ms, and prove that single-event distributed failover over-admission is strictly bounded by 2 C_max = 2,000 tokens per client key. Evaluated against 10 programmatic synthetic benchmark workloads and 17 closed-loop adversarial attack families, DREB-RCA70 suppresses adversarial farming throughput by 56.7% to 85.7% relative to a large token bucket (averaging 74.8% across evaluated attacks) and reduces credit accrual rate by 93.04% in closed-loop parameter ablation, while maintaining 0.00% false rejection for legitimate 1,000-token batch bursts under tested quiescent conditions. In distributed deployment on a 6-node Redis Cluster, DREB-RCA70 executes with an in-memory dispatch latency of 42 ns (P50), an asynchronous network round-trip latency of 0.259 ms (P50) and 0.667 ms (P99), and a synchronous replication durability latency (WAIT 1) of 0.348 ms (P50) and 1.301 ms (P99), providing resilient cloud admission control at line rate.
```

---

## 2. Keywords and Indexing

### Primary Keywords
- Rate Limiting
- API Gateway
- Credit Farming
- Admission Control
- Denial-of-Service Mitigation

### Secondary Keywords
- Token Bucket
- Rest-Conditioned Ceilings
- Burst Allowance
- Multi-Tenancy
- Cloud Infrastructure Resilience

### Methodology Keywords
- Empirical Performance Evaluation
- Microbenchmarking (JMH)
- Distributed Systems Benchmarking
- In-Memory Key-Value Stores (Redis)
- Deterministic Rate Analysis

---

## 3. Research Contribution Summary (for Submission Portals)

> DREB-RCA70 resolves the fundamental trade-off between supporting legitimate, high-volume batch bursts and preventing low-rate adversarial credit farming in multi-tenant cloud API gateways. By decoupling baseline burst capacity from an auxiliary idle credit pool modulated by a rest-conditioned abuse score, it achieves 0.00% false rejection on legitimate 1,000-token batch bursts while cutting adversarial farming throughput by 56.7% to 85.7% relative to large token buckets. The mechanism provides formal deterministic rate bounds under local and bounded physical time ($|\Delta\tau| \le 10$ ms), guarantees single-event failover over-admission bounds ($\le 2,000$ tokens per key), and evaluates at line rate with 42 ns in-memory P50 latency and sub-millisecond distributed Redis Cluster round-trips.

---

## 4. Subject Area Classifications

### ACM Computing Classification System (CCS) Concepts (for SoCC / ICPE)
- **Computer systems organization ~ Cloud computing**
  - *Context:* API gateways, reverse proxies, and microservice infrastructure.
- **Security and privacy ~ Denial-of-service attacks**
  - *Context:* Low-rate credit-farming exploitation and algorithmic burst abuse.
- **Networks ~ Network performance analysis**
  - *Context:* Admission rate bounds, throughput envelope derivations, and burst capacity.
- **Software and its engineering ~ Software performance**
  - *Context:* Sub-microsecond dispatch latency, lock-free evaluation, and distributed Redis state replication.

### IEEE Taxonomy Terms (for ToN / IEEE Journals)
- **Quality of service (QoS)**
- **Denial-of-service attack**
- **Cloud computing**
- **Access control**
- **Traffic control (communication)**

---

## 5. Author Metadata Placeholders

> [!IMPORTANT]
> The submission manuscript itself MUST remain strictly anonymous for double-anonymous venues (ACM SoCC, ACM ICPE). The metadata below is submitted exclusively through the conference/journal submission portal (HotCRP or ScholarOne).

### Author 1 (Primary / Corresponding Author)
- **Full Legal Name:** `[INSERT AUTHOR 1 FULL NAME]`
- **Institutional Affiliation:** `[INSERT UNIVERSITY / COMPANY / INSTITUTE]`
- **Department / School:** `[INSERT DEPARTMENT / LAB]`
- **City, Country/Region:** `[INSERT CITY, COUNTRY]`
- **Official Institutional Email:** `[INSERT INSTITUTIONAL EMAIL]`
- **ORCID iD:** `[INSERT 16-DIGIT ORCID, e.g., 0000-0002-XXXX-XXXX]` *(Mandatory for ACM and IEEE)*
- **Corresponding Author:** `[YES / NO]`

### Author 2
- **Full Legal Name:** `[INSERT AUTHOR 2 FULL NAME]`
- **Institutional Affiliation:** `[INSERT UNIVERSITY / COMPANY / INSTITUTE]`
- **Department / School:** `[INSERT DEPARTMENT / LAB]`
- **City, Country/Region:** `[INSERT CITY, COUNTRY]`
- **Official Institutional Email:** `[INSERT INSTITUTIONAL EMAIL]`
- **ORCID iD:** `[INSERT ORCID]`
- **Corresponding Author:** `[YES / NO]`

### Author 3 (if applicable)
- **Full Legal Name:** `[INSERT AUTHOR 3 FULL NAME]`
- **Institutional Affiliation:** `[INSERT UNIVERSITY / COMPANY / INSTITUTE]`
- **Department / School:** `[INSERT DEPARTMENT / LAB]`
- **City, Country/Region:** `[INSERT CITY, COUNTRY]`
- **Official Institutional Email:** `[INSERT INSTITUTIONAL EMAIL]`
- **ORCID iD:** `[INSERT ORCID]`
- **Corresponding Author:** `[YES / NO]`

---

## 6. Conflict of Interest (COI) Declarations

In HotCRP (SoCC / ICPE) and ScholarOne (ToN), authors must declare institutional, personal, and collaborator conflicts of interest to avoid assigning conflicted reviewers.

### Institutional Conflicts (Current and Past 24 Months)
- `[Institution A: e.g., University / Organization of current employment]`
- `[Institution B: e.g., University / Organization within last 24 months]`

### Advisory / Mentorship Conflicts (Lifetime)
- `[Ph.D. Advisor(s)]`
- `[Ph.D. Advisee(s)]`

### Co-Author & Collaborator Conflicts (Active or Last 24 Months)
- `[Collaborator Name, Institution]`
- `[Collaborator Name, Institution]`

---

## 7. Double-Blind Anonymization Verification Checklist

For submission to double-anonymous venues (ACM SoCC and ACM/SPEC ICPE Research Track):

- [ ] **Author Names and Affiliations:** Completely omitted from the title block of the PDF.
- [ ] **Email Addresses:** No author emails present anywhere in the document.
- [ ] **Acknowledgments Section:** Omitted or strictly redacted (no mentioning of specific grant numbers, university compute clusters, or internal corporate sponsorship until camera-ready).
- [ ] **Third-Person Citations:** All citations to authors' prior work are phrased in the third person (e.g., "Prior work by Smith et al. [12]" rather than "Our prior work [12]").
- [ ] **Repository Links:** No public GitHub or GitLab links containing author usernames or organization profiles. Links point to an anonymous reproducibility repository or an anonymized archive (e.g., Anonymous GitHub).
- [ ] **PDF Document Metadata:** Verified that PDF author, creator, and producer metadata fields do not contain author names or workstation logins (`exiftool paper.pdf`).
- [ ] **Artifact Package:** Source code header licenses and comments stripped of author identities and institutional copyrights.
