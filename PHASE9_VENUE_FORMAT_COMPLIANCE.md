# PHASE 9: VENUE FORMAT COMPLIANCE AND SUBMISSION SPECIFICATION

**Target Policy:** DREB-RCA70 (`com.dreb.ratelimit.policies.AdaptiveCeilingIdleCreditPolicy`)  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `4ff9d0a` / Phase 9 Working Tree  

---

## 1. Executive Summary

This document evaluates the compliance of the `DREB_RCA70_MANUSCRIPT_DRAFT.md` manuscript against the author guidelines and submission specifications of the three top-tier target venues identified in Phase 8:

1. **ACM Symposium on Cloud Computing (SoCC 2027)** — *Primary Target (Systems)*
2. **IEEE/ACM Transactions on Networking (ToN)** — *Secondary Target (Networking Journal)*
3. **ACM International Conference on Performance Engineering (ICPE 2027)** — *Alternative Target (Performance)*

---

## 2. Venue Comparison and Formatting Criteria Matrix

| Criterion | ACM SoCC | IEEE/ACM ToN | ACM ICPE | Manuscript Draft Status |
|---|---|---|---|---|
| **Manuscript Type** | Full Conference Paper | Regular Journal Paper | Research Track Paper | Fully Aligned (Systems/Measurement) |
| **Page / Word Limit** | 12 pages (excluding references/appendix) | 14 pages (standard IEEE double-column) | 12 pages (excluding references) | ~8,500 words (~11 pages in ACM `sigplan`/`acmsmall` 2-column) |
| **Review Model** | Double-Blind Review | Single-Blind or Double-Blind (Optional) | Double-Blind Review | Fully Compliant (Anonymous Authors, Neutral Citing) |
| **Document Template** | ACM Master Article Template (`acmart`, `sigconf`) | IEEE Transactions 2-column template | ACM Master Article Template (`acmart`, `sigconf`) | Markdown draft directly maps to LaTeX sections |
| **Artifact Evaluation** | Badges: Available, Functional, Reusable | Open Source / Data Availability Statement | Badges: Reproducible, Reusable | Full Reproducibility Package Provided (`PHASE9_REPRODUCIBILITY_ARTIFACT/`) |
| **CCS Concepts & Keywords**| Required | IEEE Terms / Inspec Keywords | Required | Fully Defined (see Section 3) |
| **Code / Data Availability**| Anonymized Zenodo / Git repo | Public repository URL or supplementary file | Anonymized Artifact URL | Anonymized Reproduction Package Prepared |

---

## 3. Section-by-Section Compliance Verification

### 3.1 Metadata & Anonymity (Double-Blind Requirement)
- **Author Identity:** Manuscript header specifies `Anonymous Authors` with generic institutional affiliations.
- **Self-Citation Handling:** Historical references to earlier project phases (Phase 0–7) are discussed neutrally as design iterations or empirical testbeds rather than citing author names.
- **Repository Links:** All code paths in the draft cite repository-relative paths (`src/main/java/...`, `research/comparison/...`) rather than personal GitHub user URLs.

### 3.2 ACM CCS Concepts & Keywords
The following ACM Computing Classification System (CCS) indexing is defined for ACM SoCC and ACM ICPE:
- **Primary:** *Networks ~ Cloud computing*
- **Secondary:** *Security and privacy ~ Denial-of-service defenses*
- **Tertiary:** *Computer systems organization ~ Dependable and fault-tolerant systems and networks*
- **Additional:** *Software and its engineering ~ Software performance*
- **Keywords:** Rate Limiting; Admission Control; Credit Farming; Burst Mitigation; Redis Cluster; High Availability; Cloud API Gateway.

### 3.3 Mathematical Rigor and Notation
- **Formal Statements:** Guarantees are formalized as numbered Theorems (Theorems 1–6) and Corollaries, with explicit mathematical proofs.
- **Notation Consistency:**
  - $R = 1,000.0\,\text{tokens/s}$ (Sustained rate)
  - $B_{\max} = 700.0$ (Primary liquid burst capacity)
  - $S_{\max} = 1285.7143$ (Sustained token drain capacity)
  - $I_{\max} = 450.0$ (Idle bonus pool)
  - $d = 1.5$ (Deficit spending penalty multiplier)
  - $\text{baseCap} = 450.0$, $\gamma = 0.75$ (Adaptive ceiling parameters)
  - $\lambda_{\text{rec}} = 0.05\,\text{s}^{-1}$ (Linear abuse recovery rate)
  - $\delta = 5\,\text{ms}$ (Maximum absolute NTP clock offset)
  - $2 C_{\max} = 2,000.0\,\text{tokens}$ (Single-event failover over-admission bound)

### 3.4 Publication Figures Compliance
All six required figures are generated in vector PDF (`.pdf`), vector SVG (`.svg`), and high-resolution 300 DPI raster (`.png`) formats:
1. `fig1_architecture_adaptive_ceiling`: Single-column width (3.25 in / 8.25 cm).
2. `fig2_pareto_frontiers`: Two-column width (7.0 in / 17.8 cm).
3. `fig3_adversarial_farming_suppression`: Two-column width (7.0 in / 17.8 cm).
4. `fig4_recovery_trajectories`: Single-column width (3.25 in / 8.25 cm).
5. `fig5_latency_hierarchy`: Two-column width (6.5 in / 16.5 cm).
6. `fig6_single_event_failover_envelope`: Single/Two-column width (5.5 in / 14.0 cm).

All figures use serif typography (`DejaVu Serif` / `Times New Roman`), colorblind-safe palettes, explicit data-point callouts, and clean vector rendering without rasterization artifacts.

---

## 4. Artifact Evaluation Badge Readiness

ACM and IEEE venues offer Artifact Evaluation (AE) badges. DREB-RCA70 is structured to qualify for all three premier badges:

1. **Artifacts Available:**
   - Package includes full source code, scripts, raw CSV datasets, and test manifests.
   - Cryptographically hashed via `PHASE9_REPRODUCIBILITY_ARTIFACT/checksums.sha256`.
2. **Artifacts Functional:**
   - Complete build verification via Maven 3.9 (`pom.xml`) with zero custom native compilation.
   - Docker Compose recipe (`docker-compose.cluster.yml`) deploys the exact 6-node Redis 7.4.11 cluster.
   - Unit, adversarial, and distributed integration tests pass deterministically.
3. **Results Replicated:**
   - Automated reproduction benchmark `./PHASE9_REPRODUCIBILITY_ARTIFACT/run_reproducibility_benchmark.sh` reproduces the entire pipeline in under 3 minutes.
   - Raw datasets verify 0.00% FRR on benign bursts and 81.9% / 56.7% / 85.7% suppression on adversarial suites.

---

## 5. Venue Selection Strategy and Timeline

1. **Target 1: ACM SoCC (Submission Deadline: Spring / Summer 2027)**
   - *Rationale:* Premier cloud systems venue. DREB-RCA70's focus on cloud API gateways, Redis Cluster distributed systems, and PACELC latency trade-offs is a direct fit for the SoCC audience.
2. **Target 2: IEEE/ACM Transactions on Networking (ToN) (Rolling Submission)**
   - *Rationale:* Premier networking journal. Suitable if an extended version with formal queueing derivations and network-wide multi-gateway coordination is preferred over a conference timeline.
3. **Target 3: ACM ICPE (Submission Deadline: Fall 2026 / 2027)**
   - *Rationale:* Focus on performance engineering, microbenchmarking, and measurement. Strong fit for the multi-scope nanosecond-to-millisecond latency evaluation and JMH harness.
