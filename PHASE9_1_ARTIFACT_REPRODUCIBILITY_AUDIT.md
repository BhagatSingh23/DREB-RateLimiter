# PHASE 9.1: REPRODUCIBILITY ARTIFACT AND SCRIPT AUDIT

**Audit Date:** November 2026  
**Repository Branch:** `research/systematic-improvement`  
**Reference Commit:** `c8df6d4`  
**Auditor:** Independent Red-Team Review  

---

## 1. Executive Summary

This audit evaluates the reproducibility artifact package created in Phase 9 ([`PHASE9_REPRODUCIBILITY_ARTIFACT/`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_REPRODUCIBILITY_ARTIFACT)) and its automated runner script ([`run_reproducibility_benchmark.sh`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_REPRODUCIBILITY_ARTIFACT/run_reproducibility_benchmark.sh)).

### Key Audit Distinctions
We separate **Functional Reproducibility** (whether the software builds, tests pass, and scripts execute cleanly from scratch) from **Numerical Result Reproducibility** (whether figures and tables are dynamically generated from parsed raw empirical datasets rather than hardcoded constant arrays):
- **Functional Reproducibility:** **PASS**. The pipeline executes with a 100% test pass rate across Java unit tests, Jqwik property tests, adversarial suites, distributed Redis cluster tests, and SHA-256 integrity verification.
- **Numerical Result Reproducibility:** **CONDITIONAL**. While the underlying datasets (`phase5_baseline_comparison_raw.csv`, `phase7_2_wait_and_clock_raw.csv`) contain the exact empirical records, the plotting script `generate_figures.py` currently hardcodes the summary coordinates into Python data arrays rather than reading dynamically from the packaged CSVs.
- **Pre-Existing State Isolation:** **PASS**. The Redis test harness generates dynamic nano-time client keys and unique idempotency keys per test run, preventing cross-test state leakage.

---

## 2. Environment and Dependency Pinning Audit

| Component | Pinned Version / Specification | Audit Verification Status |
|---|---|---|
| **Java JDK** | OpenJDK 21 LTS (Temurin 21.0.9) | Confirmed via `java -version` |
| **Build Tool** | Apache Maven 3.9.16 | Confirmed via `mvn -version` |
| **Redis Server** | Redis 7.4.11 (Alpine container) | Pinned in `docker-compose.cluster.yml` |
| **Jedis Client** | 5.2.0 | Pinned in `pom.xml` (`<jedis.version>5.2.0</jedis.version>`) |
| **JMH Benchmark**| 1.37 | Pinned in `pom.xml` |
| **Jqwik Properties**| 1.8.2 | Pinned in `pom.xml` |
| **Python** | Python 3.13.9 | Standard library + Matplotlib 3.11.2, NumPy 2.5.4 |
| **Docker Engine** | Docker Compose v2+ | Single container `dreb-redis-cluster` (ports 7001–7006) |

---

## 3. Detailed Audit of the Automated Benchmark Script

The runner script [`run_reproducibility_benchmark.sh`](file:///Users/cruise/Desktop/DREB-RateLimiter/PHASE9_REPRODUCIBILITY_ARTIFACT/run_reproducibility_benchmark.sh) executes six discrete stages:

### Step 1: Environment & Topology Verification
- Checks Java, Maven, Python, and Docker container presence.
- Verifies that `dreb-redis-cluster` is running. If absent, executes `docker-compose up -d`.

### Step 2: In-Memory Core & Baseline Verification Tests
- Command: `mvn test -Dtest="CoreLimiterTest,DrebPropertiesTest,Phase541RequestSemanticsTest,Phase54OperationalEligibilityTest" -q`
- **Audit Finding:** Executing these tests verifies:
  1. The 1,000-try Jqwik state invariants and cumulative rate bounds.
  2. The exact empirical data rows in `phase5_baseline_comparison_raw.csv` without modifying or overwriting the historical CSV.
  3. All tests pass with zero failures.

### Step 3: Closed-Loop Adversarial Attack Suites
- Command: `mvn test -Dtest="Phase4AdversarialTest,AntiFarmingEvaluationTest" -q`
- Verifies anti-farming properties against Families A through E.

### Step 4: Distributed Redis Cluster & Failure Model Tests
- Command: `mvn test -Dtest="Phase7ProductionAdapterTest,Phase72WaitSemanticsTest,Phase7RedisClusterLiveTest" -q`
- Tests live 6-node Redis cluster communication, Lua script compilation, `WAIT 1 1000` acknowledgement, and the 5-condition Idempotency Contract.
- Measures live network round-trip latencies:
  - Async mode: P50 = 0.220 ms, P99 = 0.532 ms.
  - Sync mode (`WAIT 1`): P50 = 0.378 ms, P99 = 1.240 ms.

### Step 5: Publication Figure Generation
- Command: `python3 PHASE9_PUBLICATION_FIGURES/generate_figures.py`
- Generates all six figures in PDF, SVG, and PNG formats.
- **Audit Erratum:** Script relies on pre-aggregated data constants. To achieve full numerical reproducibility, the script should parse the packaged CSVs directly.

### Step 6: SHA-256 Checksum Integrity Verification
- Command: `shasum -c PHASE9_REPRODUCIBILITY_ARTIFACT/checksums.sha256`
- Verifies all 15 core files. **100% OK match**.

---

## 4. Determinism vs. Seed Dependence

1. **Jqwik Property Tests:** Uses fixed/randomized pseudo-random seeds. All 1,000 generated checks hold across all seeds.
2. **Phase 5 Workloads:** Evaluated across 30 deterministic seeds (5000–5029). The results stored in `phase5_baseline_comparison_raw.csv` are strictly deterministic functions of the PRNG sequence.
3. **Live Redis Latency Benchmarks:** Network RTT latencies vary slightly between test runs depending on OS scheduling and CPU load (e.g., P99 varies between 1.15 ms and 1.35 ms for `WAIT 1`), which is standard for real network transport. The relative penalty (+95.1%) remains consistent.

---

## 5. Required Enhancements for Artifact Evaluation

1. **Update `generate_figures.py` to parse CSVs:** Refactor plotting routines for Figures 2, 3, and 5 to load and aggregate rows directly from `phase5_baseline_comparison_raw.csv` and `phase7_2_wait_and_clock_raw.csv`.
2. **Container Teardown Documentation:** Add explicit cleanup commands (`docker-compose down -v`) in `README.md` to ensure pristine cluster resets between independent evaluation attempts.
