# PHASE 10: ARTIFACT RELEASE READINESS AND ANONYMIZATION AUDIT

**Repository:** `DREB-RateLimiter`  
**Branch:** `submission/phase10-preparation`  
**Reference Commit:** `1040cc4`  
**Artifact Package:** [`PHASE9_REPRODUCIBILITY_ARTIFACT/`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_REPRODUCIBILITY_ARTIFACT)  
**Auditor:** Publication Preparation Review Panel  

---

## 1. Executive Summary

This audit assesses the readiness of the DREB-RCA70 reproducibility artifact package for formal Artifact Evaluation (AE) at ACM conferences (SoCC, ICPE). It classifies the package across the four recognized reproducibility tiers, specifies the anonymization steps required for double-anonymous peer review, and defines the packaging and licensing structure.

In strict compliance with Phase 10 rules, **no public upload, external release, or Zenodo archiving is performed during this phase.**

---

## 2. Four-Tier Reproducibility Classification

```
+---------------------------------------------------------------------------------------------------------+
| Table 1: Reproducibility Evaluation across Standard Scientific Tiers.                                   |
+----+------------------------------------+-----------+---------------------------------------------------+
| #  | Reproducibility Tier               | Status    | Verification Evidence                             |
+----+------------------------------------+-----------+---------------------------------------------------+
| 1  | **Artifact Availability**          | **PASS**  | Complete Java source code, Redis Lua script,      |
|    |                                    |           | Docker Compose files, and raw CSV datasets are    |
|    |                                    |           | present and verified in repository.               |
+----+------------------------------------+-----------+---------------------------------------------------+
| 2  | **Functional Execution**           | **PASS**  | Automated runner script executes end-to-end with  |
|    |                                    |           | 100% test pass rate across all 6 test stages.     |
+----+------------------------------------+-----------+---------------------------------------------------+
| 3  | **Numerical Result Reproduction**  | **PASS**  | Package contains frozen historical raw CSVs       |
|    |                                    |           | (`phase5_baseline_comparison_raw.csv`), SHA-256   |
|    |                                    |           | manifests, and figure regeneration scripts.       |
+----+------------------------------------+-----------+---------------------------------------------------+
| 4  | **Independent-Environment**        | **NOT YET | Executed on local macOS ARM64 environment; multi- |
|    | **Replication**                    | PERFORMED | host bare-metal deployment with physical PTP/NTP  |
|    |                                    |           | clocks remains future operational work.           |
+----+------------------------------------+-----------+---------------------------------------------------+
```

---

## 3. Double-Anonymous Review Anonymization Checklist

For submission to ACM SoCC or ACM ICPE (which mandate double-anonymous review), the artifact package must be scrubbed of all author-identifying metadata prior to upload to private evaluation servers:

### [ ] 1. File Path Scrubbing
- *Audit Finding:* Some local logs and historical markdown documents contain absolute user directory paths (e.g., `/Users/cruise/...`).
- *Required Action:* When packaging the artifact ZIP/tarball, convert all absolute path references to relative paths (`./`) or container-relative paths (`/workspace/...`).

### [ ] 2. Git History Anonymization
- *Audit Finding:* Git commit history contains committer names and email addresses.
- *Required Action:* Do not link to a personal GitHub or GitLab repository in the double-blind paper. Package the artifact as an anonymized standalone `.zip` archive or upload to an anonymous Zenodo/Figshare deposit with GitHub author tracking disabled.

### [ ] 3. Code Header and Author Comments
- *Audit Finding:* Code comments throughout `src/main` and `src/test` cite neutral package names (`com.dreb.ratelimit`), which is compliant.
- *Required Action:* Verify that no author names, copyright notices, or institutional email addresses appear in Java source files.

---

## 4. Anonymized Artifact Package Inventory

An anonymized artifact tarball (`dreb-rca70-artifact-anonymized.tar.gz`) should include the following directory layout:

```
dreb-rca70-artifact/
├── README.md                           # Venue-neutral reproduction guide (see Section 5)
├── pom.xml                             # Pinned Maven build configuration (Java 21)
├── checksums.sha256                    # Cryptographic verification hashes
├── run_benchmark.sh                    # Automated end-to-end verification script
├── src/
│   ├── main/java/com/dreb/ratelimit/   # Production DREB-RCA70 Java & Redis Lua implementations
│   └── test/java/com/dreb/ratelimit/   # JMH microbenchmarks, invariant tests, cluster tests
├── docker/
│   └── docker-compose.cluster.yml      # 6-node Redis 7.4.11 cluster deployment recipe
├── data/
│   ├── phase3_ablation_raw.csv         # Parameter ablation raw measurements
│   ├── phase5_baseline_raw.csv         # 10-workload baseline comparison raw data
│   └── phase7_redis_raw.csv            # Distributed Lua, async, and WAIT 1 latency records
└── figures/
    ├── generate_figures.py             # Reproducible plotting script for Figures 1–6
    └── output/                         # Pre-rendered vector (PDF, SVG) and raster (PNG) figures
```

---

## 5. Venue-Neutral Artifact README Template

```markdown
# Reproducibility Artifact: DREB-RCA70 Admission Control

This artifact package provides full source code, deployment recipes, raw empirical datasets, and automated test scripts to reproduce the findings presented in the paper:
*"DREB-RCA70: Mitigating Low-Rate Credit Farming in Cloud API Gateways via Rest-Conditioned Adaptive Idle-Credit Ceilings"*.

## 1. System Requirements
- OS: Linux x86_64 / ARM64 or macOS ARM64.
- Java: OpenJDK 21 LTS (Temurin recommended).
- Build Tool: Apache Maven 3.9+.
- Container Runtime: Docker Engine 24+ and Docker Compose v2+.
- Python: Python 3.10+ with `matplotlib>=3.8` and `numpy>=1.24`.

## 2. Quick Start (Single-Command Execution)
To build the project, run in-memory baseline validations, execute distributed Redis Cluster tests, regenerate all publication figures, and verify cryptographic checksums:
```bash
./run_benchmark.sh
```
Expected execution duration: ~2–3 minutes.

## 3. Verified Claims and Data Provenance
1. Benign Burst Tolerance: 0.00% False Rejection Rate for 1,000-token bursts under quiescent conditions.
2. Adversarial Farming Suppression: 81.9% (W04), 56.7% (W08), and 85.7% (W10) reduction relative to Token Bucket.
3. Execution Latency: In-memory Java dispatch executes in 42 ns (P50); distributed Redis asynchronous network RTT executes in 0.259 ms (P50) and 0.667 ms (P99); synchronous replication durability (`WAIT 1`) requires 0.348 ms (P50) and 1.301 ms (P99).
```

---

## 6. Software Licensing

- The DREB-RCA70 reference implementation is structured for open-source distribution under the **Apache License, Version 2.0**.
- Raw CSV datasets and publication figures are structured for distribution under the **Creative Commons Attribution 4.0 International (CC BY 4.0)** license.
- Final licensing selection remains subject to human author confirmation.
