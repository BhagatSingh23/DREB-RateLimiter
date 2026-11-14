# CANONICAL RESEARCH VERSION PROVENANCE & FORMULATION SEPARATION

**Audit Date:** November 14, 2026  
**Primary Focus:** Disambiguation of the DREB Proportional/Burst-Matched Critique vs. Canonical DREB-RCA70  
**Status:** COMPLETE — Formulations Strictly Disambiguated and Provenance Established  

---

## 1. Executive Summary & Root Cause of Version Conflict

During pre-submission audits, an apparent contradiction surfaced between two distinct algorithmic formulations:

1. **Formulation A (The Baseline Empirical Study & Critique):**
   - Parametrization: $S_{\max}=C$, $B_{\max}=C/3$, $\text{idle\_cap}=2C/3$, $\text{idle\_decay}=1.5$ (Proportional mapping) and $B_{\max}=3C/7$, $\text{idle\_cap}=6C/7$, $S_{\max}=9C/7$ (Burst-matched mapping).
   - Instantaneous burst: $7C/9$ (proportional) and $C$ (burst-matched).
   - Cumulative admission: $C+RT$ (proportional) and $9C/7+RT$ (burst-matched).
   - Documented in: 21-page draft *"Idle-Conditioned Burst Limiting: An Empirical Study of the Dual-Rate Elastic Bucket (DREB), with a Corrected Specification"*.
   - Evaluated in repository: `DREB-Comparator` (tag `v1.0-eval`, commit `76beaca` / `cf0f3f8`) using library tag `v1.0.2-phase1` (commit `53a5c9e`).

2. **Formulation B (The Canonical NICE-TEAS ASIA 2026 Submission: DREB-RCA70):**
   - Parametrization: Calibrated for gateway rate $R = 1,000\,\text{tokens/s}$.
   - Baseline liquid burst bucket: $B_{\max} = 700$ tokens ($70\%$ liquid baseline).
   - Auxiliary idle credit pool: $\text{baseCap} = 450$ tokens.
   - Rest-conditioned adaptive ceiling: $I_{\text{ceil}}(K) = \frac{\text{baseCap}}{1 + \gamma K}$, where $\gamma = 0.75$.
   - Continuous linear recovery: $\lambda_{\text{rec}} = 0.05\,\text{s}^{-1}$.
   - Asymmetric deficit debit penalty: $d = 1.5$.
   - Quiescent maximum burst headroom: $C_{\text{burst,max}} = B_{\max} + \frac{I_{\text{ceil}}(0)}{d} = 700 + \frac{450}{1.5} = 1,000$ tokens.
   - Sustained envelope: $S_{\max} = 1285.7143$, $S_{\text{rate}} = R = 1,000\,\text{tokens/s}$.
   - Local-time cumulative bound ($G_1$): $A(T) \le 1285.7143 + 1000 \cdot T$.
   - Bounded physical-time envelope ($G_{1,\delta}$): $A(t) \le 1295.7143 + 1000 \cdot t$ under $|\Delta \tau| \le 10\,\text{ms}$.
   - Documented in: The 10-page full manuscript (`latex/main.pdf`) and 8-page compact manuscript (`compact-8-page/latex/main.pdf`).
   - Repository: `DREB-RateLimiter` branch `submission/nice-teas-asia-2026` (ancestry: `1040cc4` -> `3209870` -> `5d8bfad` -> `adf3b1d` -> `3e5e49e`).

**Definitive Finding:**  
These two formulations represent **sequential evolutionary stages of the research program**, not conflicting descriptions of the same algorithm. Formulation A was the preliminary, flawed baseline algorithm whose severe contract over-enforcement defects motivated the design of Formulation B (DREB-RCA70). 

---

## 2. Chronological & Conceptual Evolutionary Lineage

The relationship between the two formulations is established through unbroken Git commit history:

```
+-----------------------------------------------------------------------------------------------+
| STAGE 1: Empirical Critique of Original DREB (October 2026)                                  |
| Repository: DREB-Comparator (v1.0-eval @ 76beaca) | Library: DREB-RateLimiter (v1.0.2-phase1)  |
| Formulation: Proportional (B_max=C/3, idle_cap=2C/3) & Burst-Matched (B_max=3C/7, idle_cap=6C/7)|
| Findings:                                                                                     |
|   1. Conjunction trap: Mild overload (k=1.10) choked to 0.806R (under-serving contract).     |
|   2. High False Rejection: Compliant bursty clients suffered 11.9% FRR at 0.783R load.        |
|   3. Static Dual-Bucket dominated DREB in 29 of 50 evaluated scenarios.                     |
|   4. Farming vulnerability: Periodic abusers gained +0.27R to +0.36R over static dual-bucket. |
| Manuscript: "Idle-Conditioned Burst Limiting: An Empirical Study..." (21-page report)        |
+-----------------------------------------------------------------------------------------------+
                                               │
                                               ▼
+-----------------------------------------------------------------------------------------------+
| STAGE 2: Root-Cause Mathematical Diagnosis & RCA70 Synthesis (November 11, 2026)              |
| Repository: DREB-RateLimiter (Branch: research/systematic-improvement)                         |
| Commit: ffddeb0 ("feat(research): complete Phase 2 over-enforcement resolution")              |
| Evidence:                                                                                     |
|   - PHASE2_1_ROOT_CAUSE.md: Mathematically proves conjunction bottleneck chokes effective     |
|     token generation to (2/3 R) / 1.5 = 4/9 R ≈ 0.444R, predicting Table 2.2 within 0.2%.     |
|   - PHASE2_2_MAPPING_CANDIDATES.md: Formulates RCA60, RCA70, and RCA80 to resolve trap.       |
|   - PHASE2_3_EXPERIMENTS.md: Evaluates 11 configurations across 6,050 simulation runs.        |
|   - PHASE2_4_PARETO_ANALYSIS.md: Proves DREB-RCA70 dominates across FRR, overload throughput,  |
|     and Theorem 2 asymptotic anti-farming bound (limsup C_credit/T <= 450 * 0.05 = 0.0225R).   |
+-----------------------------------------------------------------------------------------------+
                                               │
                                               ▼
+-----------------------------------------------------------------------------------------------+
| STAGE 3: Formal Claim Freeze Manifest & Final Research Baseline (November 11, 2026)          |
| Repository: DREB-RateLimiter (Branch: research/systematic-improvement)                         |
| Commit: 1040cc4 (Phase 9.2 Claim Freeze Manifest & Verification Checklist)                    |
| Canonical Frozen Configuration:                                                               |
|   R=1000, B_max=700, baseCap=450, d=1.5, gamma=0.75, lambda_rec=0.05 s^-1                   |
|   10 synthetic workloads (W01-W10), 17 closed-loop attacks, 30 seeds (5000-5029)            |
+-----------------------------------------------------------------------------------------------+
                                               │
                                               ▼
+-----------------------------------------------------------------------------------------------+
| STAGE 4: Submission Adaptation for NICE-TEAS ASIA 2026 (November 12-14, 2026)                |
| Repository: DREB-RateLimiter (Branch: submission/nice-teas-asia-2026)                         |
| Commits: 3209870 (Phase 10) -> 5d8bfad (Manuscript) -> adf3b1d (Phase 11) -> 3e5e49e (Phase 12)|
| Canonical Submission Artifacts:                                                                |
|   - 10-Page Full Manuscript: submission/nice-teas-asia-2026/latex/main.pdf                    |
|   - 8-Page Compact Manuscript: submission/nice-teas-asia-2026/compact-8-page/latex/main.pdf    |
+-----------------------------------------------------------------------------------------------+
```

---

## 3. Comprehensive Version Provenance Matrix

| Research Artifact | Repository | Branch / Ref | Commit / Tag | Algorithmic Formulation | Belongs to NICE-TEAS RCA70? | Concrete Evidence / Location |
| :--- | :--- | :--- | :---: | :--- | :---: | :--- |
| **DREB Reference Library** | `DREB-RateLimiter` | `main` | `53a5c9e`<br>(tag `v1.0.2-phase1`) | Original DREB v1/v2 ($B_{\max}=3C/7, S_{\max}=9C/7$) | **No** (Antecedent library) | `pom.xml`, `CHANGES.md`, `src/main/java/com/dreb/ratelimit/DREBLimiter.java` |
| **DREB Benchmark Suite** | `DREB-Comparator` | `main` | `cf0f3f8`<br>(tag `v1.0-eval` @ `76beaca`) | Original DREB v1/v2 vs. 5 classical algorithms | **No** (Antecedent benchmark) | `results/PAPER_TABLES.md`, `src/main/java/com/ratelimiter/experiments/` |
| **Early Critique Manuscript** | Desktop / Uploaded | N/A | Superseded | Proportional ($7C/9$) & Burst-Matched ($9C/7+RT$) | **No** (Earlier 21-page draft) | `Idle-Conditioned Burst Limiting An Empirical Study of.pdf` |
| **Over-Enforcement Diagnosis** | `DREB-RateLimiter` | `research/systematic-improvement` | `ffddeb0` | Diagnosis of $0.806R$ over-enforcement & conjunction trap | **Transition link** | `research/over_enforcement/PHASE2_1_ROOT_CAUSE.md` |
| **RCA70 Implementation** | `DREB-RateLimiter` | `research/systematic-improvement` | `ffddeb0` | DREB-RCA70 ($B_{\max}=700, \text{baseCap}=450, d=1.5, \gamma=0.75$) | **Yes** (Foundational code) | `src/test/java/com/dreb/ratelimit/policies/DrebRcaPolicyFactory.java` |
| **Property Test Suite** | `DREB-RateLimiter` | `research/systematic-improvement` | `ffddeb0` | jqwik verification of $G_1, G_2, G_3$, Theorem 2 | **Yes** (Verification suite) | `src/test/java/com/dreb/ratelimit/DrebRcaPropertiesTest.java` |
| **Claim Freeze Manifest** | `DREB-RateLimiter` | `research/systematic-improvement` | `1040cc4` | Frozen RCA70 metrics across W01–W10 & Attacks A–E | **Yes** (Canonical source) | `PHASE9_2_CLAIM_FREEZE_MANIFEST.json` |
| **Venue Adaptation** | `DREB-RateLimiter` | `submission/phase10-preparation` | `3209870` | RCA70 IEEE format adaptation | **Yes** (Venue package) | `submission/phase10/PHASE10_VENUE_COMPARISON.md` |
| **10-Page LaTeX & PDF** | `DREB-RateLimiter` | `submission/nice-teas-asia-2026` | `5d8bfad` / `3e5e49e` | DREB-RCA70 IEEEtran (10 pages) | **Yes** (Full edition) | `submission/nice-teas-asia-2026/latex/main.tex` & `main.pdf` |
| **8-Page LaTeX & PDF** | `DREB-RateLimiter` | `submission/nice-teas-asia-2026` | `adf3b1d` / `3e5e49e` | DREB-RCA70 IEEEtran (8 pages) | **Yes** (Compact edition) | `submission/nice-teas-asia-2026/compact-8-page/latex/main.tex` & `main.pdf` |

---

## 4. Explicit Prevention of Cross-Contamination

To prevent any cross-contamination between the two distinct projects:
1. **Manuscript Isolation:** The LaTeX sources for the NICE-TEAS submission (`submission/nice-teas-asia-2026/latex/main.tex` and `compact-8-page/latex/main.tex`) contain **zero** occurrences of proportional mapping equations ($C/3, 2C/3, 7C/9$), old capacity multipliers ($3C/7, 6C/7$), or obsolete comparator experiment IDs (E1–E12).
2. **Bibliographic Positioning:** The earlier empirical study of baseline DREB is treated strictly as an antecedent benchmark report that revealed the conjunction trap, motivating the invention of DREB-RCA70.
3. **Repository Boundaries:**
   - `DREB-Comparator` remains the permanent, frozen repository for the classical algorithm benchmark study.
   - `DREB-RateLimiter` contains the canonical DREB-RCA70 implementation, the complete Phase 1–12 research audit trail, and the active NICE-TEAS ASIA 2026 publication package.
