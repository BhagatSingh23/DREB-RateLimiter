package com.dreb.ratelimit;

import com.dreb.ratelimit.policies.AdaptiveCeilingIdleCreditPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5.4: Operational Pareto Eligibility Consistency Audit Test Suite.
 *
 * Validates:
 * 1. Reconstructed legitimate burst and post-attack request/token metrics from phase5_baseline_comparison_raw.csv.
 * 2. Empirical/simulation validation of Static DB vs RCA70 on nominal C=1000 post-idle burst capacity.
 * 3. Formal verification that Static DB's 30.0% burst rejection at C=1000 violates the 5.0% FRR SLA.
 * 4. Operational Pareto frontier calculation under Scope 1 (c <= 700) and Scope 2 (elastic C = 1000).
 * 5. Elimination of the contradictory 3-policy hybrid frontier.
 */
@DisplayName("Phase 5.4: Operational Eligibility Consistency Audit")
public class Phase54OperationalEligibilityTest {

    private static final double C = 1000.0;
    private static final double R = 1000.0;
    private static final double SLA_FRR_THRESHOLD = 5.0; // 5.0% maximum tolerable FRR

    /**
     * Record representing raw aggregated legitimate performance per workload from CSV.
     */
    public record WorkloadLegitStats(
            String configId,
            String workloadId,
            double offeredLegitTokens,
            double admittedLegitTokens,
            double rejectedLegitTokens,
            double frrPercent,
            double burstAccPercent
    ) {}

    /**
     * Parse raw CSV and compute average metrics across all 30 seeds.
     */
    private Map<String, Map<String, WorkloadLegitStats>> parseRawCsvLegitStats() throws IOException {
        String csvPath = "research/comparison/phase5_baseline_comparison_raw.csv";
        Map<String, Map<String, List<double[]>>> raw = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String header = br.readLine();
            assertNotNull(header);
            String line;
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",");
                String cfg = cols[0];
                String wId = cols[1];
                double offLeg = Double.parseDouble(cols[19]);
                double admLeg = Double.parseDouble(cols[20]);
                double frr = Double.parseDouble(cols[21]);
                double bar = Double.parseDouble(cols[22]);

                raw.computeIfAbsent(cfg, k -> new HashMap<>())
                   .computeIfAbsent(wId, k -> new ArrayList<>())
                   .add(new double[]{offLeg, admLeg, frr, bar});
            }
        }

        Map<String, Map<String, WorkloadLegitStats>> result = new HashMap<>();
        for (var cfgEntry : raw.entrySet()) {
            String cfg = cfgEntry.getKey();
            result.put(cfg, new HashMap<>());
            for (var wEntry : cfgEntry.getValue().entrySet()) {
                String wId = wEntry.getKey();
                List<double[]> list = wEntry.getValue();
                double avgOff = list.stream().mapToDouble(d -> d[0]).average().orElse(0.0);
                double avgAdm = list.stream().mapToDouble(d -> d[1]).average().orElse(0.0);
                double avgRej = avgOff - avgAdm;
                double avgFrr = list.stream().mapToDouble(d -> d[2]).average().orElse(0.0);
                double avgBar = list.stream().mapToDouble(d -> d[3]).average().orElse(0.0);

                result.get(cfg).put(wId, new WorkloadLegitStats(cfg, wId, avgOff, avgAdm, avgRej, avgFrr, avgBar));
            }
        }
        return result;
    }

    /**
     * 1. Reconstruct legitimate burst FRR (W02) and post-attack FRR (W10) directly from CSV.
     */
    @Test
    @DisplayName("Task A: Reconstruct legitimate metrics from Phase 5 raw CSV")
    void testReconstructLegitimateMetricsFromRawCsv() throws IOException {
        var stats = parseRawCsvLegitStats();

        // 1. W02: 39 bursts of 700 tokens (offered = 27,300 tokens)
        WorkloadLegitStats rcaW02 = stats.get("rca70").get("W02_legitimate_on_off_burst");
        assertEquals(27300.0, rcaW02.offeredLegitTokens(), 1e-3);
        assertEquals(27300.0, rcaW02.admittedLegitTokens(), 1e-3);
        assertEquals(0.0, rcaW02.frrPercent(), 1e-3);
        assertEquals(100.0, rcaW02.burstAccPercent(), 1e-3);

        WorkloadLegitStats staticW02 = stats.get("static_db_070c").get("W02_legitimate_on_off_burst");
        assertEquals(27300.0, staticW02.offeredLegitTokens(), 1e-3);
        assertEquals(27300.0, staticW02.admittedLegitTokens(), 1e-3);
        assertEquals(0.0, staticW02.frrPercent(), 1e-3);
        assertEquals(100.0, staticW02.burstAccPercent(), 1e-3);

        WorkloadLegitStats tbW02 = stats.get("tb_large").get("W02_legitimate_on_off_burst");
        assertEquals(27300.0, tbW02.offeredLegitTokens(), 1e-3);
        assertEquals(27300.0, tbW02.admittedLegitTokens(), 1e-3);
        assertEquals(0.0, tbW02.frrPercent(), 1e-3);
        assertEquals(100.0, tbW02.burstAccPercent(), 1e-3);

        WorkloadLegitStats drebAcW02 = stats.get("dreb_ac_bm").get("W02_legitimate_on_off_burst");
        assertEquals(27300.0, drebAcW02.offeredLegitTokens(), 1e-3);
        assertEquals(9800.0, drebAcW02.admittedLegitTokens(), 1e-3);
        assertEquals(17500.0, drebAcW02.rejectedLegitTokens(), 1e-3);
        assertEquals(64.10, drebAcW02.frrPercent(), 0.05);
        assertEquals(35.90, drebAcW02.burstAccPercent(), 0.05);

        // 2. W10: 20 bursts of 700 tokens in recovery phase [30s, 60s] (offered = 14,000 tokens)
        WorkloadLegitStats drebAcW10 = stats.get("dreb_ac_bm").get("W10_post_attack_recovery");
        assertEquals(14000.0, drebAcW10.offeredLegitTokens(), 1e-3);
        assertEquals(5600.0, drebAcW10.admittedLegitTokens(), 1e-3);
        assertEquals(8400.0, drebAcW10.rejectedLegitTokens(), 1e-3);
        assertEquals(60.0, drebAcW10.frrPercent(), 1e-3);
        assertEquals(40.0, drebAcW10.burstAccPercent(), 1e-3);

        WorkloadLegitStats rcaW10 = stats.get("rca70").get("W10_post_attack_recovery");
        assertEquals(0.0, rcaW10.frrPercent(), 1e-3);

        WorkloadLegitStats staticW10 = stats.get("static_db_070c").get("W10_post_attack_recovery");
        assertEquals(0.0, staticW10.frrPercent(), 1e-3);
    }

    /**
     * 2. Direct simulation of nominal C=1000 post-idle burst capacity using actual limiter implementations.
     */
    @Test
    @DisplayName("Task B: Direct execution of nominal C=1000 burst on actual limiter instances")
    void testDirectLimiterExecutionOnNominalBurst() {
        ManualClock clock = new ManualClock(0.0);

        // A. Static Dual Bucket: B_max = 700, zero idle credit
        InMemoryStateStore staticStore = new InMemoryStateStore();
        ConstantRateRefillStrategy staticRefill = new ConstantRateRefillStrategy(700.0, 5.0 * R / 3.0, 9.0 * C / 7.0, R);
        LinearIdleCreditPolicy staticPolicy = new LinearIdleCreditPolicy(0.0, 0.0, 1.5);
        DREBLimiter staticLimiter = new DREBLimiter(staticStore, clock, staticRefill, staticPolicy, 9.0 * C / 7.0);

        // Initialize state at t=0.0 and advance 10s to ensure full refill
        clock.set(0.0);
        staticLimiter.allow("client_atomic", 1);
        clock.set(10.0);

        // Test 1: Single atomic request of cost 1000
        // Because effective burst is clamped to 700, an atomic request of 1000 must be DENIED!
        Decision staticAtomic = staticLimiter.allow("client_atomic", 1000);
        assertEquals(Decision.DENY, staticAtomic, "Static DB must deny an atomic burst request exceeding B_max=700");

        // Test 2: Stream of 1000 unit tokens (cost 1 each)
        clock.set(0.0);
        staticLimiter.allow("client_stream", 1);
        clock.set(10.0);
        int admittedStaticTokens = 0;
        for (int i = 0; i < 1000; i++) {
            if (staticLimiter.allow("client_stream", 1) == Decision.ALLOW) {
                admittedStaticTokens++;
            }
        }
        assertEquals(700, admittedStaticTokens, "Static DB must admit exactly B_max=700 unit tokens");
        int rejectedStaticTokens = 1000 - admittedStaticTokens;
        assertEquals(300, rejectedStaticTokens);

        double staticFrrTokens = 100.0 * rejectedStaticTokens / 1000.0;
        assertEquals(30.0, staticFrrTokens, 1e-6);

        // Static DB violates 5% SLA under BOTH atomic (100% FRR) and streaming (30% FRR) interpretations
        assertTrue(staticFrrTokens > SLA_FRR_THRESHOLD, "Static DB 30% FRR violates the 5% operational threshold");

        // B. RCA70: B_max = 700, idle credit up to 450 (provides 300 effective burst tokens via 450/1.5)
        InMemoryStateStore rcaStore = new InMemoryStateStore();
        ConstantRateRefillStrategy rcaRefill = new ConstantRateRefillStrategy(700.0, 5.0 * R / 3.0, 9.0 * C / 7.0, R);
        AdaptiveCeilingIdleCreditPolicy rcaPolicy = new AdaptiveCeilingIdleCreditPolicy(1.5 * R, 450.0, 1.5, 0.75, 0.05);
        DREBLimiter rcaLimiter = new DREBLimiter(rcaStore, clock, rcaRefill, rcaPolicy, 9.0 * C / 7.0);

        // Initialize state at t=0.0 and advance 10s to ensure full idle credit accumulation
        clock.set(0.0);
        rcaLimiter.allow("client_atomic", 1);
        clock.set(10.0);

        // Test 1: Atomic request of cost 1000
        Decision rcaAtomic = rcaLimiter.allow("client_atomic", 1000);
        assertEquals(Decision.ALLOW, rcaAtomic, "RCA70 must allow atomic burst of 1000 using base burst (700) + idle credit (300)");

        // Test 2: Stream of 1000 unit tokens
        clock.set(0.0);
        rcaLimiter.allow("client_stream", 1);
        clock.set(10.0);
        int admittedRcaTokens = 0;
        for (int i = 0; i < 1000; i++) {
            if (rcaLimiter.allow("client_stream", 1) == Decision.ALLOW) {
                admittedRcaTokens++;
            }
        }
        assertEquals(1000, admittedRcaTokens, "RCA70 must admit all 1000 tokens using idle credit");

        // C. Token Bucket: Capacity = 1000
        InMemoryStateStore tbStore = new InMemoryStateStore();
        TokenBucketLimiter tbLimiter = new TokenBucketLimiter(tbStore, clock, C, R);
        clock.set(0.0);
        tbLimiter.allow("client_atomic", 1);
        clock.set(10.0);
        Decision tbAtomic = tbLimiter.allow("client_atomic", 1000);
        assertEquals(Decision.ALLOW, tbAtomic, "Token Bucket must allow nominal burst of 1000");
    }

    /**
     * Objective vector representing the 6 evaluated dimensions.
     */
    public record CandidatePolicy(
            String name,
            double frrBurst,      // FRR on burst workload (minimize)
            double barBurst,      // Burst Acceptance Rate (maximize)
            double frrPost,       // FRR on post-attack recovery (minimize)
            double advRate,       // Adversarial rate (minimize)
            double creditRate,    // Credit rate (minimize)
            double latencyP99     // Latency ns (minimize)
    ) {
        public boolean isOperationallyEligible(double maxFrr) {
            return this.frrBurst <= maxFrr && this.frrPost <= maxFrr;
        }

        public boolean dominates(CandidatePolicy other) {
            boolean noWorse = this.frrBurst <= other.frrBurst &&
                    this.barBurst >= other.barBurst &&
                    this.frrPost <= other.frrPost &&
                    this.advRate <= other.advRate &&
                    this.creditRate <= other.creditRate &&
                    this.latencyP99 <= other.latencyP99;

            boolean strictlyBetter = this.frrBurst < other.frrBurst ||
                    this.barBurst > other.barBurst ||
                    this.frrPost < other.frrPost ||
                    this.advRate < other.advRate ||
                    this.creditRate < other.creditRate ||
                    this.latencyP99 < other.latencyP99;

            return noWorse && strictlyBetter;
        }
    }

    /**
     * 3. Task C & D: Operational Frontier under Scope 1 (Constrained Bursts c <= 700).
     */
    @Test
    @DisplayName("Task C & D: Verify Operational Frontier under Scope 1 (Constrained Bursts c <= 700)")
    void testOperationalFrontierScope1Constrained() {
        // Under Scope 1, burst requirements are capped at 700 tokens (Phase 5 W02)
        CandidatePolicy rca70 = new CandidatePolicy("rca70", 0.0, 100.0, 0.0, 0.3467, 0.0667, 208.0);
        CandidatePolicy staticDb = new CandidatePolicy("static_db_070c", 0.0, 100.0, 0.0, 0.0000, 0.0000, 83.0);
        CandidatePolicy tbLarge = new CandidatePolicy("tb_large", 0.0, 100.0, 0.0, 0.8000, 0.0000, 125.0);
        CandidatePolicy drebAc = new CandidatePolicy("dreb_ac_bm", 64.10, 35.90, 60.0, 0.1200, 0.0589, 42.0);
        CandidatePolicy drebV2 = new CandidatePolicy("dreb_v2_default", 2.56, 97.44, 0.0, 0.7067, 0.3214, 125.0);

        List<CandidatePolicy> all = List.of(rca70, staticDb, tbLarge, drebAc, drebV2);

        // Filter eligible policies
        List<CandidatePolicy> eligible = all.stream()
                .filter(p -> p.isOperationallyEligible(SLA_FRR_THRESHOLD))
                .toList();

        // Under Scope 1, DREB-AC is excluded, while Static DB, RCA70, TB, and DREB v2 are eligible
        assertFalse(eligible.contains(drebAc), "DREB-AC BM must be excluded due to FRR > 5%");
        assertTrue(eligible.contains(staticDb), "Static DB is eligible when bursts are capped at 700");
        assertTrue(eligible.contains(rca70), "RCA70 is eligible");
        assertTrue(eligible.contains(tbLarge), "Token Bucket is eligible");
        assertTrue(eligible.contains(drebV2), "DREB v2 is eligible");

        // Verify Pareto dominance among eligible policies in Scope 1:
        // Static DB strictly dominates RCA70, Token Bucket, and DREB v2!
        assertTrue(staticDb.dominates(rca70), "Static DB dominates RCA70 under Scope 1");
        assertTrue(staticDb.dominates(tbLarge), "Static DB dominates Token Bucket under Scope 1");
        assertTrue(staticDb.dominates(drebV2), "Static DB dominates DREB v2 under Scope 1");

        // Therefore, the Operational Frontier under Scope 1 is strictly {Static DB}!
        List<CandidatePolicy> frontierScope1 = eligible.stream()
                .filter(p -> eligible.stream().noneMatch(other -> other != p && other.dominates(p)))
                .toList();

        assertEquals(1, frontierScope1.size(), "Scope 1 operational frontier must contain only Static DB");
        assertEquals("static_db_070c", frontierScope1.get(0).name());
    }

    /**
     * 4. Task C & D: Operational Frontier under Scope 2 (Elastic Nominal Bursts C = 1000).
     */
    @Test
    @DisplayName("Task C & D: Verify Operational Frontier under Scope 2 (Elastic Nominal Bursts C = 1000)")
    void testOperationalFrontierScope2Elastic() {
        // Under Scope 2, the operational SLA requires accommodating nominal bursts up to C = 1000
        // Static DB suffers 30% FRR (70% BAR), violating the 5% threshold!
        CandidatePolicy rca70 = new CandidatePolicy("rca70", 0.0, 100.0, 0.0, 0.3467, 0.0667, 208.0);
        CandidatePolicy staticDb = new CandidatePolicy("static_db_070c", 30.0, 70.0, 0.0, 0.0000, 0.0000, 83.0);
        CandidatePolicy tbLarge = new CandidatePolicy("tb_large", 0.0, 100.0, 0.0, 0.8000, 0.0000, 125.0);
        CandidatePolicy drebAc = new CandidatePolicy("dreb_ac_bm", 70.0, 30.0, 60.0, 0.1200, 0.0589, 42.0);
        CandidatePolicy drebV2 = new CandidatePolicy("dreb_v2_default", 10.0, 90.0, 0.0, 0.7067, 0.3214, 125.0);

        List<CandidatePolicy> all = List.of(rca70, staticDb, tbLarge, drebAc, drebV2);

        // Filter eligible policies
        List<CandidatePolicy> eligible = all.stream()
                .filter(p -> p.isOperationallyEligible(SLA_FRR_THRESHOLD))
                .toList();

        // Under Scope 2, Static DB and DREB-AC are EXCLUDED!
        assertFalse(eligible.contains(staticDb), "Static DB MUST BE EXCLUDED from Scope 2 (30% FRR > 5%)");
        assertFalse(eligible.contains(drebAc), "DREB-AC BM must be excluded from Scope 2");
        assertFalse(eligible.contains(drebV2), "DREB v2 must be excluded from Scope 2 (10% FRR > 5%)");

        assertTrue(eligible.contains(rca70), "RCA70 must be eligible under Scope 2");
        assertTrue(eligible.contains(tbLarge), "Token Bucket must be eligible under Scope 2");

        // Verify Pareto dominance between RCA70 and Token Bucket:
        // Neither dominates the other!
        assertFalse(rca70.dominates(tbLarge), "RCA70 does not dominate Token Bucket");
        assertFalse(tbLarge.dominates(rca70), "Token Bucket does not dominate RCA70");

        // Compute frontier
        List<CandidatePolicy> frontierScope2 = eligible.stream()
                .filter(p -> eligible.stream().noneMatch(other -> other != p && other.dominates(p)))
                .toList();

        assertEquals(2, frontierScope2.size(), "Scope 2 operational frontier must contain exactly {RCA70, Token Bucket}");
        List<String> names = frontierScope2.stream().map(CandidatePolicy::name).sorted().toList();
        assertEquals(List.of("rca70", "tb_large"), names);
    }

    /**
     * 5. Formal mathematical demonstration: The 3-policy hybrid frontier in Phase 5.3 was inconsistent.
     */
    @Test
    @DisplayName("Task E: Prove Phase 5.3 3-policy frontier was a hybrid contradiction")
    void testProvePhase53HybridContradiction() {
        // In Phase 5.3:
        // Claim: Operational Frontier = {RCA70, Static DB, Token Bucket}
        //
        // Contradiction Proof:
        // If Static DB is eligible -> bursts must be evaluated at c <= 700 (where FRR = 0%).
        // BUT at c <= 700, Static DB dominates RCA70 and TB, making {RCA70, Static DB, TB} invalid (dominance failure).
        //
        // If bursts are evaluated at C = 1000 to prevent Static DB from dominating RCA70 ->
        // THEN Static DB has FRR = 30% > 5%, making Static DB ineligible (eligibility failure).
        //
        // Under NO consistent mathematical scope does the set {RCA70, Static DB, TB} simultaneously satisfy
        // both eligibility (FRR <= 5%) and mutual non-dominance!

        boolean consistentUnderScope1 = false;
        boolean consistentUnderScope2 = false;

        // Check Scope 1
        CandidatePolicy rca70_1 = new CandidatePolicy("rca70", 0.0, 100.0, 0.0, 0.3467, 0.0667, 208.0);
        CandidatePolicy staticDb_1 = new CandidatePolicy("static_db_070c", 0.0, 100.0, 0.0, 0.0000, 0.0000, 83.0);
        CandidatePolicy tbLarge_1 = new CandidatePolicy("tb_large", 0.0, 100.0, 0.0, 0.8000, 0.0000, 125.0);

        boolean allEligible1 = rca70_1.isOperationallyEligible(5.0) &&
                staticDb_1.isOperationallyEligible(5.0) &&
                tbLarge_1.isOperationallyEligible(5.0);
        boolean mutuallyNonDominated1 = !rca70_1.dominates(staticDb_1) && !staticDb_1.dominates(rca70_1) &&
                !staticDb_1.dominates(tbLarge_1) && !tbLarge_1.dominates(staticDb_1);
        consistentUnderScope1 = allEligible1 && mutuallyNonDominated1;

        // Check Scope 2
        CandidatePolicy rca70_2 = new CandidatePolicy("rca70", 0.0, 100.0, 0.0, 0.3467, 0.0667, 208.0);
        CandidatePolicy staticDb_2 = new CandidatePolicy("static_db_070c", 30.0, 70.0, 0.0, 0.0000, 0.0000, 83.0);
        CandidatePolicy tbLarge_2 = new CandidatePolicy("tb_large", 0.0, 100.0, 0.0, 0.8000, 0.0000, 125.0);

        boolean allEligible2 = rca70_2.isOperationallyEligible(5.0) &&
                staticDb_2.isOperationallyEligible(5.0) &&
                tbLarge_2.isOperationallyEligible(5.0);
        boolean mutuallyNonDominated2 = !rca70_2.dominates(staticDb_2) && !staticDb_2.dominates(rca70_2) &&
                !staticDb_2.dominates(tbLarge_2) && !tbLarge_2.dominates(staticDb_2);
        consistentUnderScope2 = allEligible2 && mutuallyNonDominated2;

        assertFalse(consistentUnderScope1, "Scope 1 fails mutual non-dominance because Static DB dominates RCA70 and TB");
        assertFalse(consistentUnderScope2, "Scope 2 fails eligibility because Static DB violates 5% FRR");

        // Thus, the Phase 5.3 3-policy frontier is proven to be a hybrid inconsistency!
        assertFalse(consistentUnderScope1 || consistentUnderScope2,
                "The 3-policy frontier cannot exist under any single consistent workload scope");
    }
}
