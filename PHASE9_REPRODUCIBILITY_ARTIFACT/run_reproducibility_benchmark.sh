#!/usr/bin/env bash
# ==============================================================================
# DREB-RCA70 Phase 9 Reproducibility Benchmark & Verification Script
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

echo "========================================================================"
echo "DREB-RCA70 ARTIFACT REPRODUCIBILITY BENCHMARK"
echo "Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "Root Dir : ${ROOT_DIR}"
echo "========================================================================"

# ------------------------------------------------------------------------------
# 1. Environment Verification
# ------------------------------------------------------------------------------
echo -e "\n[1/6] Checking Environment..."
java -version 2>&1 | head -n 1
mvn -version 2>&1 | head -n 1
python3 --version 2>&1 | head -n 1

# Check docker status
if docker ps --format '{{.Names}}' | grep -q 'dreb-redis-cluster'; then
    echo "Redis Cluster container 'dreb-redis-cluster' is running."
else
    echo "WARNING: 'dreb-redis-cluster' container is not running. Starting testbed..."
    docker-compose -f research/redis/deploy/docker-compose.cluster.yml up -d || true
fi

# ------------------------------------------------------------------------------
# 2. In-Memory Baseline & Core Validation Tests
# ------------------------------------------------------------------------------
echo -e "\n[2/6] Executing In-Memory Core & Baseline Verification Tests..."
mvn test -Dtest="CoreLimiterTest,DrebPropertiesTest,Phase541RequestSemanticsTest,Phase54OperationalEligibilityTest" -q
echo ">> In-Memory tests PASSED."

# ------------------------------------------------------------------------------
# 3. Closed-Loop Adversarial Tests
# ------------------------------------------------------------------------------
echo -e "\n[3/6] Executing Phase 4 Adversarial Attack Suites..."
mvn test -Dtest="Phase4AdversarialTest,AntiFarmingEvaluationTest" -q
echo ">> Adversarial attack tests PASSED."

# ------------------------------------------------------------------------------
# 4. Distributed Redis & Failure Model Tests
# ------------------------------------------------------------------------------
echo -e "\n[4/6] Executing Distributed Redis Cluster & Failure Model Tests..."
mvn test -Dtest="Phase7ProductionAdapterTest,Phase72WaitSemanticsTest,Phase7RedisClusterLiveTest" -q
echo ">> Distributed Redis tests PASSED."

# ------------------------------------------------------------------------------
# 5. Publication Figure Generation
# ------------------------------------------------------------------------------
echo -e "\n[5/6] Regenerating Publication Figures (PDF, SVG, PNG)..."
python3 PHASE9_PUBLICATION_FIGURES/generate_figures.py
echo ">> Publication figures generated successfully."

# ------------------------------------------------------------------------------
# 6. Integrity & Checksum Verification
# ------------------------------------------------------------------------------
echo -e "\n[6/6] Verifying SHA-256 Checksums..."
shasum -c PHASE9_REPRODUCIBILITY_ARTIFACT/checksums.sha256
echo ">> All artifact checksums verified OK."

echo "========================================================================"
echo "DREB-RCA70 REPRODUCIBILITY SUITE COMPLETED SUCCESSFULLY: ALL PASS"
echo "========================================================================"
