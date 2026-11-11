# DREB-RCA70 Reproducibility Artifact

This artifact package provides full reproduction instructions, configuration definitions, and automated verification scripts for all experimental results and mathematical bounds presented in the DREB-RCA70 manuscript.

---

## 1. System Requirements

- **Operating System:** macOS (Apple Silicon ARM64) or Linux x86_64 / ARM64.
- **Java Development Kit:** OpenJDK 21 LTS (tested with Temurin 21.0.9).
- **Build Tool:** Apache Maven 3.9+.
- **Container Engine:** Docker Engine 24+ and Docker Compose v2+.
- **Database:** Redis 7.4.11 (containerized cluster configuration provided).
- **Python Environment:** Python 3.10+ with `matplotlib>=3.8` and `numpy>=1.24`.

---

## 2. Quick-Start One-Command Reproduction

To run the complete automated validation suite (environment check, in-memory baselines, adversarial attack suites, distributed Redis cluster tests, publication figures regeneration, and checksum verification):

```bash
./PHASE9_REPRODUCIBILITY_ARTIFACT/run_reproducibility_benchmark.sh
```

---

## 3. Directory Layout and Key Components

```
DREB-RateLimiter/
├── PHASE9_REPRODUCIBILITY_ARTIFACT/
│   ├── README.md                           # This guide
│   ├── checksums.sha256                    # Cryptographic SHA-256 hashes of all raw data & code
│   └── run_reproducibility_benchmark.sh    # Automated end-to-end execution script
├── PHASE9_PUBLICATION_FIGURES/
│   ├── generate_figures.py                 # Reproducible plotting script for Figures 1-6
│   └── fig*.{pdf,svg,png}                  # Publication-ready vector and raster figures
├── PHASE9_CLAIM_RECONCILIATION.md           # Claim reconciliation and provenance audit
├── DREB_RCA70_MANUSCRIPT_DRAFT.md          # 12-section submission-ready manuscript
├── research/
│   ├── ablation/                           # Phase 3 parameter ablation reports & raw CSV
│   ├── comparison/                         # Phase 5 baseline comparison reports & raw CSV
│   └── redis/                              # Phase 6 & Phase 7 Redis durability, WAIT, & clock audits
└── src/
    ├── main/java/com/dreb/ratelimit/       # Core rate limiting engine and Redis adapter
    └── test/java/com/dreb/ratelimit/       # Microbenchmarks, adversarial tests, live cluster tests
```

---

## 4. Calibrated DREB-RCA70 Configuration Parameters

All evaluations use the frozen RCA70 operational configuration:

| Parameter | Identifier in Code | Value | Description |
|-----------|-------------------|-------|-------------|
| Refill Rate | `refillRate` / $R$ | `1000.0` | Sustained capacity refill tokens/second |
| Primary Capacity | `primaryCapacity` / $B_{\max}$ | `700.0` | Immediate liquid burst bucket limit |
| Sustained Capacity | `sustainedCapacity` / $S_{\max}$ | `1285.7143` | Macroscopic token drain bound ($G_1$) |
| Idle Credit Max | `idleCreditCapacity` / $I_{\max}$ | `450.0` | Quiescent bonus credit pool |
| Deficit Penalty | `deficitMultiplier` / $d$ | `1.5` | Idle credit penalty per deficit token |
| Base Ceiling | `baseCap` | `450.0` | Nominal quiescent burst ceiling |
| Ceiling Sensitivity | `gamma` / $\gamma$ | `0.75` | Hyperbolic abuse suppression factor |
| Recovery Rate | `recoveryRate` / $\lambda_{\text{rec}}$ | `0.05` | Linear abuse score decay ($s^{-1}$) |

---

## 5. Step-by-Step Manual Reproduction

### Step 5.1: In-Memory Core & Baseline Comparison
Executes core rate limiter invariant tests, request semantics audits, and Pareto eligibility evaluations against `tb_standard`, `tb_large`, `static_db_070c`, and `gcra`:
```bash
mvn test -Dtest="CoreLimiterTest,DrebPropertiesTest,Phase541RequestSemanticsTest,Phase54OperationalEligibilityTest"
```

### Step 5.2: Phase 4 Adversarial Attack Suites
Evaluates closed-loop adaptive adversaries attempting credit farming:
```bash
mvn test -Dtest="Phase4AdversarialTest,AntiFarmingEvaluationTest"
```

### Step 5.3: Start Distributed Redis Cluster
Spins up a 6-node Redis 7.4.11 cluster across ports 7001–7006:
```bash
docker-compose -f research/redis/deploy/docker-compose.cluster.yml up -d
```

### Step 5.4: Distributed Failover & Idempotency Tests
Tests atomic Lua execution, `WAIT 1 1000` replication acknowledgement, failover over-admission, and the 5-condition Idempotency Contract:
```bash
mvn test -Dtest="Phase7ProductionAdapterTest,Phase72WaitSemanticsTest,Phase7RedisClusterLiveTest"
```

### Step 5.5: Generate Figures
```bash
python3 PHASE9_PUBLICATION_FIGURES/generate_figures.py
```

### Step 5.6: Verify Artifact Checksums
```bash
shasum -c PHASE9_REPRODUCIBILITY_ARTIFACT/checksums.sha256
```
