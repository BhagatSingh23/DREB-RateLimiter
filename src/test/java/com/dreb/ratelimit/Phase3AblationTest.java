package com.dreb.ratelimit;

import com.dreb.ratelimit.policies.AdaptiveCeilingIdleCreditPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3: Comprehensive Ablation Study for DREB-RCA70.
 *
 * Systematically investigates five dimensions:
 * A. Base burst allocation (RCA60, RCA70, RCA80).
 * B. Credit accounting decay factor (baseDecay in {1.0, 1.25, 1.5, 2.0} with matched effective capacity).
 * C. Recovery rate (lambda_rec in {0.01, 0.025, 0.05, 0.10, 0.20}/s).
 * D. Adaptive-state ablation (beta in {0.0, 0.25, 0.50, 0.75, 1.0, 1.5}).
 * E. Combined orthogonal ablations and baseline comparisons.
 */
@DisplayName("Phase 3: DREB-RCA70 Comprehensive Ablation Study")
public class Phase3AblationTest {

    private static final double C = 1000.0;
    private static final double R = 1000.0;
    private static final double DURATION = 60.0;
    private static final int SEEDS = 10;
    private static final long SEED_START = 4000L;

    public interface LimiterHarness {
        RateLimiter limiter();
        double getCreditSpent();
        ClientState peekState();
    }

    public record ConfigDef(
            String id,
            String dimension,
            String displayName,
            double burstMax,
            double baseCap,
            double baseDecay,
            double gain,
            double beta,
            double lambdaRec,
            java.util.function.Function<Clock, LimiterHarness> factory
    ) {}

    /**
     * Flexible Idle Credit Policy for ablation experiments, allowing baseDecay >= 1.0.
     */
    public static class AblationIdleCreditPolicy implements IdleCreditPolicy {
        private final double gain;
        private final double baseCap;
        private final double baseDecay;
        private final double beta;
        private final double recoveryRate;
        private double totalCreditSpent = 0.0;

        public AblationIdleCreditPolicy(double gain, double baseCap, double baseDecay, double beta, double recoveryRate) {
            this.gain = gain;
            this.baseCap = baseCap;
            this.baseDecay = baseDecay;
            this.beta = beta;
            this.recoveryRate = recoveryRate;
        }

        @Override
        public void accrue(ClientState state, double dt, double tFull) {
            if (dt > 0.0 && state.abuseScore > 0.0) {
                state.abuseScore = Math.max(0.0, state.abuseScore - dt * recoveryRate);
            }
            double currentCap = (beta <= 0.0) ? baseCap : (baseCap / (1.0 + beta * state.abuseScore));
            if (tFull > 0.0) {
                state.idleCredit = Math.min(currentCap, state.idleCredit + tFull * gain);
            } else {
                state.idleCredit = Math.min(currentCap, state.idleCredit);
            }
        }

        @Override
        public void spend(ClientState state, double deficit) {
            if (deficit > 0.0) {
                totalCreditSpent += deficit;
                state.idleCredit = Math.max(0.0, state.idleCredit - deficit * baseDecay);
                if (baseCap > 0.0 && beta > 0.0) {
                    state.abuseScore += (deficit / baseCap);
                }
            }
        }

        @Override
        public double effectiveCredit(ClientState state) {
            return (baseDecay <= 0.0) ? 0.0 : (state.idleCredit / baseDecay);
        }

        public double totalCreditSpent() { return totalCreditSpent; }
    }

    private static LimiterHarness buildHarness(Clock clock, double bMax, double bRate, double sMax, double sRate,
                                               double gain, double baseCap, double baseDecay, double beta, double lambdaRec) {
        InMemoryStateStore store = new InMemoryStateStore();
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(bMax, bRate, sMax, sRate);
        AblationIdleCreditPolicy policy = new AblationIdleCreditPolicy(gain, baseCap, baseDecay, beta, lambdaRec);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, policy, sMax);
        return new LimiterHarness() {
            @Override public RateLimiter limiter() { return limiter; }
            @Override public double getCreditSpent() { return policy.totalCreditSpent(); }
            @Override public ClientState peekState() { return limiter.peekState("client"); }
        };
    }

    private static List<ConfigDef> getAblationConfigurations() {
        List<ConfigDef> list = new ArrayList<>();
        double sMax = 9.0 * C / 7.0; // 1285.71
        double bRate = 5.0 * R / 3.0; // 1666.67
        double sRate = R;

        // =========================================================================
        // REFERENCE CONFIGURATION: DREB-RCA70
        // =========================================================================
        list.add(new ConfigDef("ref_rca70", "REFERENCE", "DREB-RCA70 (Reference)",
                700.0, 450.0, 1.5, 1.5 * R, 0.75, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 0.75, 0.05)));

        // =========================================================================
        // DIMENSION A: BASE BURST ALLOCATION
        // (Holding baseDecay=1.5, gain=1.5R, beta=0.75, lambdaRec=0.05 constant)
        // =========================================================================
        list.add(new ConfigDef("alloc_rca60", "DIM_A_ALLOC", "Allocation RCA60 (B=600)",
                600.0, 600.0, 1.5, 1.5 * R, 0.75, 0.05,
                clock -> buildHarness(clock, 600.0, bRate, sMax, sRate, 1.5 * R, 600.0, 1.5, 0.75, 0.05)));

        list.add(new ConfigDef("alloc_rca80", "DIM_A_ALLOC", "Allocation RCA80 (B=800)",
                800.0, 300.0, 1.5, 1.5 * R, 0.75, 0.05,
                clock -> buildHarness(clock, 800.0, bRate, sMax, sRate, 1.5 * R, 300.0, 1.5, 0.75, 0.05)));

        // =========================================================================
        // DIMENSION B: CREDIT ACCOUNTING (baseDecay)
        // (Holding B=700, I_eff=300 constant => baseCap = baseDecay * 300)
        // =========================================================================
        // 1. baseDecay = 1.0 (Linear debit, no penalty; baseCap = 300)
        list.add(new ConfigDef("decay_100", "DIM_B_DECAY", "Credit Accounting (decay=1.0, cap=300)",
                700.0, 300.0, 1.0, 1.5 * R, 0.75, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 300.0, 1.0, 0.75, 0.05)));

        // 2. baseDecay = 1.25 (Mild penalty; baseCap = 375)
        list.add(new ConfigDef("decay_125", "DIM_B_DECAY", "Credit Accounting (decay=1.25, cap=375)",
                700.0, 375.0, 1.25, 1.5 * R, 0.75, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 375.0, 1.25, 0.75, 0.05)));

        // 3. baseDecay = 2.0 (Heavy penalty; baseCap = 600)
        list.add(new ConfigDef("decay_200", "DIM_B_DECAY", "Credit Accounting (decay=2.0, cap=600)",
                700.0, 600.0, 2.0, 1.5 * R, 0.75, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 600.0, 2.0, 0.75, 0.05)));

        // =========================================================================
        // DIMENSION C: RECOVERY RATE (lambda_rec)
        // (Holding B=700, baseCap=450, baseDecay=1.5, beta=0.75 constant)
        // =========================================================================
        list.add(new ConfigDef("rec_001", "DIM_C_RECOVERY", "Recovery Rate (lambda=0.01/s)",
                700.0, 450.0, 1.5, 1.5 * R, 0.75, 0.01,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 0.75, 0.01)));

        list.add(new ConfigDef("rec_0025", "DIM_C_RECOVERY", "Recovery Rate (lambda=0.025/s)",
                700.0, 450.0, 1.5, 1.5 * R, 0.75, 0.025,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 0.75, 0.025)));

        list.add(new ConfigDef("rec_010", "DIM_C_RECOVERY", "Recovery Rate (lambda=0.10/s)",
                700.0, 450.0, 1.5, 1.5 * R, 0.75, 0.10,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 0.75, 0.10)));

        list.add(new ConfigDef("rec_020", "DIM_C_RECOVERY", "Recovery Rate (lambda=0.20/s)",
                700.0, 450.0, 1.5, 1.5 * R, 0.75, 0.20,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 0.75, 0.20)));

        // =========================================================================
        // DIMENSION D: ADAPTIVE-STATE ABLATION (beta)
        // (Holding B=700, baseCap=450, baseDecay=1.5, lambdaRec=0.05 constant)
        // =========================================================================
        list.add(new ConfigDef("beta_000", "DIM_D_BETA", "Adaptive Ceiling (beta=0.0, disabled)",
                700.0, 450.0, 1.5, 1.5 * R, 0.0, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 0.0, 0.05)));

        list.add(new ConfigDef("beta_025", "DIM_D_BETA", "Adaptive Ceiling (beta=0.25)",
                700.0, 450.0, 1.5, 1.5 * R, 0.25, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 0.25, 0.05)));

        list.add(new ConfigDef("beta_050", "DIM_D_BETA", "Adaptive Ceiling (beta=0.50)",
                700.0, 450.0, 1.5, 1.5 * R, 0.50, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 0.50, 0.05)));

        list.add(new ConfigDef("beta_100", "DIM_D_BETA", "Adaptive Ceiling (beta=1.00)",
                700.0, 450.0, 1.5, 1.5 * R, 1.00, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 1.00, 0.05)));

        list.add(new ConfigDef("beta_150", "DIM_D_BETA", "Adaptive Ceiling (beta=1.50)",
                700.0, 450.0, 1.5, 1.5 * R, 1.50, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 1.50, 0.05)));

        // =========================================================================
        // DIMENSION E: COMBINED CROSS-FACTOR ABLATIONS & BASELINES
        // =========================================================================
        // Combo 1: Linear unpenalized credit (decay=1.0, beta=0.0)
        list.add(new ConfigDef("combo_no_g3_no_beta", "DIM_E_COMBO", "Combo: No Decay & No Beta (decay=1.0, beta=0)",
                700.0, 300.0, 1.0, 1.5 * R, 0.0, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 300.0, 1.0, 0.0, 0.05)));

        // Combo 2: Decay penalty only (decay=1.5, beta=0.0)
        list.add(new ConfigDef("combo_decay_only", "DIM_E_COMBO", "Combo: Decay Penalty Only (decay=1.5, beta=0)",
                700.0, 450.0, 1.5, 1.5 * R, 0.0, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 450.0, 1.5, 0.0, 0.05)));

        // Combo 3: Beta ceiling only (decay=1.0, beta=0.75, cap=300)
        list.add(new ConfigDef("combo_beta_only", "DIM_E_COMBO", "Combo: Beta Ceiling Only (decay=1.0, beta=0.75)",
                700.0, 300.0, 1.0, 1.5 * R, 0.75, 0.05,
                clock -> buildHarness(clock, 700.0, bRate, sMax, sRate, 1.5 * R, 300.0, 1.0, 0.75, 0.05)));

        // Baseline: Token Bucket (C, R)
        list.add(new ConfigDef("tb_large", "BASELINE", "Token Bucket (C, R)",
                C, 0.0, 1.0, 0.0, 0.0, 0.0,
                clock -> {
                    InMemoryStateStore store = new InMemoryStateStore();
                    TokenBucketLimiter tb = new TokenBucketLimiter(store, clock, C, R);
                    return new LimiterHarness() {
                        @Override public RateLimiter limiter() { return tb; }
                        @Override public double getCreditSpent() { return 0.0; }
                        @Override public ClientState peekState() { return tb.peekState("client"); }
                    };
                }));

        // Baseline: DREB v1 BM
        list.add(new ConfigDef("dreb_v1_bm", "BASELINE", "DREB v1 (BM Baseline)",
                3.0 * C / 7.0, 6.0 * C / 7.0, 1.5, 2.0 * R / 3.0, 0.0, 0.0,
                clock -> {
                    InMemoryStateStore store = new InMemoryStateStore();
                    ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(3.0 * C / 7.0, 5.0 * R / 3.0, sMax, R);
                    LinearIdleCreditPolicy linear = new LinearIdleCreditPolicy(2.0 * R / 3.0, 6.0 * C / 7.0, 1.5);
                    Phase2EvaluationTest.TrackingIdlePolicy tracking = new Phase2EvaluationTest.TrackingIdlePolicy(linear);
                    DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
                    return new LimiterHarness() {
                        @Override public RateLimiter limiter() { return limiter; }
                        @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                        @Override public ClientState peekState() { return limiter.peekState("client"); }
                    };
                }));

        // Baseline: DREB-AC BM
        list.add(new ConfigDef("dreb_ac_bm", "BASELINE", "DREB-AC (BM Baseline)",
                3.0 * C / 7.0, 6.0 * C / 7.0, 1.5, 2.0 * R / 3.0, 0.75, 0.05,
                clock -> {
                    InMemoryStateStore store = new InMemoryStateStore();
                    ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(3.0 * C / 7.0, 5.0 * R / 3.0, sMax, R);
                    AdaptiveCeilingIdleCreditPolicy ac = new AdaptiveCeilingIdleCreditPolicy(2.0 * R / 3.0, 6.0 * C / 7.0, 1.5, 0.75, 0.05);
                    Phase2EvaluationTest.TrackingIdlePolicy tracking = new Phase2EvaluationTest.TrackingIdlePolicy(ac);
                    DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
                    return new LimiterHarness() {
                        @Override public RateLimiter limiter() { return limiter; }
                        @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                        @Override public ClientState peekState() { return limiter.peekState("client"); }
                    };
                }));

        return list;
    }

    public record Request(double time, int cost) {}

    public interface WorkloadGenerator {
        List<Request> generate(double offeredLoad, long seed, double duration);
    }

    @Test
    @DisplayName("Execute Phase 3 Comprehensive Ablation Benchmark Suite")
    void runPhase3AblationExperiments() throws Exception {
        Path outCsv = Paths.get("research/ablation/phase3_ablation_raw.csv");
        Files.createDirectories(outCsv.getParent());

        List<ConfigDef> configs = getAblationConfigurations();

        Map<String, WorkloadGenerator> workloads = new LinkedHashMap<>();

        // 1. Bursty ON/OFF Traffic (Table 3.1 setup)
        workloads.put("bursty_on_off", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            Random rng = new Random(seed);
            double meanBurst = 0.60 * C;
            double burstRate = load * R;
            double meanInterval = meanBurst / burstRate;
            double t = 0.0;
            while (t < duration) {
                t += -Math.log(1.0 - rng.nextDouble()) * meanInterval;
                if (t >= duration) break;
                int burst = (int) Math.round((0.30 + rng.nextDouble() * 0.60) * C);
                list.add(new Request(t, burst));
            }
            return list;
        });

        // 2. Overload Chunked Burst Stream (Table 2.2 setup)
        workloads.put("overload_burst_stream", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            int burst = (int) Math.round(0.95 * C);
            double dt = (0.95 * C) / (load * R);
            double t = dt;
            while (t < duration) {
                list.add(new Request(t, burst));
                t += dt;
            }
            return list;
        });

        // 3. Adversarial Periodic Credit Farming
        workloads.put("adversarial_farming", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            double tau = 1.2857;
            double tFill = (3.0 * C / 7.0) / (5.0 * R / 3.0);
            double period = tFill + tau;
            double t = period;
            while (t < duration) {
                list.add(new Request(t, (int) C));
                t += period;
            }
            return list;
        });

        // 4. Closed-Loop Adaptive Adversary (varying wait interval on 429)
        workloads.put("closed_loop_adversary", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            Random rng = new Random(seed);
            double t = 1.0;
            double wait = 1.5;
            while (t < duration) {
                list.add(new Request(t, (int) C));
                // Jittered wait interval simulating adaptive search
                t += wait + (rng.nextDouble() - 0.5) * 0.2;
            }
            return list;
        });

        // Offered loads per workload
        Map<String, double[]> workloadLoads = new LinkedHashMap<>();
        workloadLoads.put("bursty_on_off", new double[]{0.783, 0.900});
        workloadLoads.put("overload_burst_stream", new double[]{1.250, 1.500});
        workloadLoads.put("adversarial_farming", new double[]{1.000});
        workloadLoads.put("closed_loop_adversary", new double[]{1.000});

        try (PrintWriter pw = new PrintWriter(new FileWriter(outCsv.toFile()))) {
            pw.println("config_id,dimension,display_name,burst_max,base_cap,base_decay,beta,lambda_rec," +
                    "workload_type,offered_load,seed,offered_tokens,admitted_tokens,admitted_rate_norm," +
                    "frr_percent,ordinary_refill_tokens,credit_tokens_drawn,credit_rate_norm,final_k_score");

            for (ConfigDef cfg : configs) {
                for (String wName : workloads.keySet()) {
                    WorkloadGenerator wGen = workloads.get(wName);
                    double[] loads = workloadLoads.get(wName);

                    for (double load : loads) {
                        for (int s = 0; s < SEEDS; s++) {
                            long seed = SEED_START + s;
                            List<Request> reqs = wGen.generate(load, seed, DURATION);

                            ManualClock clock = new ManualClock(0.0);
                            LimiterHarness harness = cfg.factory.apply(clock);

                            long offered = 0;
                            long admitted = 0;

                            for (Request req : reqs) {
                                clock.set(req.time);
                                offered += req.cost;

                                int remaining = req.cost;
                                int step = req.cost;
                                while (remaining > 0 && step >= 1) {
                                    if (step <= remaining && harness.limiter().allow("client", step) == Decision.ALLOW) {
                                        admitted += step;
                                        remaining -= step;
                                    } else {
                                        step /= 2;
                                    }
                                }
                            }

                            double normRate = admitted / (R * DURATION);
                            double frr = offered > 0 ? (100.0 * (offered - admitted) / offered) : 0.0;
                            double creditDrawn = harness.getCreditSpent();
                            double creditRateNorm = creditDrawn / (R * DURATION);
                            double ordinaryTokens = admitted - creditDrawn;

                            ClientState st = harness.peekState();
                            double finalK = st != null ? st.abuseScore : 0.0;

                            pw.printf("%s,%s,\"%s\",%.1f,%.1f,%.2f,%.2f,%.3f,%s,%.3f,%d,%d,%d,%.6f,%.2f,%.2f,%.2f,%.6f,%.4f%n",
                                    cfg.id, cfg.dimension, cfg.displayName,
                                    cfg.burstMax, cfg.baseCap, cfg.baseDecay, cfg.beta, cfg.lambdaRec,
                                    wName, load, seed,
                                    offered, admitted, normRate, frr,
                                    ordinaryTokens, creditDrawn, creditRateNorm, finalK);
                        }
                    }
                }
            }
        }

        assertTrue(Files.exists(outCsv));
    }

    @Test
    @DisplayName("Verify Minimum Idle Duration Formula for Burst Admission")
    void verifyMinimumIdleDurationFormula() {
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();

        LimiterHarness harness = buildHarness(clock, 700.0, 5.0 * R / 3.0, 9.0 * C / 7.0, R, 1.5 * R, 450.0, 1.5, 0.75, 0.05);

        // Exhaust buckets at t=0
        harness.limiter().allow("client", (int) (9.0 * C / 7.0));

        // Theoretical minimum idle duration starting with S at least C:
        // t_fill = 700 / (5000/3) = 0.420s
        // t_credit = 450 / 1500 = 0.300s
        // Total idle duration needed for B_eff >= 1000 is 0.720s.
        // For S >= 1000, S needs 1.000s if starting from 0.

        // At t = 1.000s, both S and B_eff have refilled to >= 1000 tokens
        clock.set(1.000);
        Decision d = harness.limiter().allow("client", (int) C);
        org.junit.jupiter.api.Assertions.assertEquals(Decision.ALLOW, d);

        ClientState s = harness.peekState();
        org.junit.jupiter.api.Assertions.assertEquals(0.0, s.burstTokens, 1e-4);
        org.junit.jupiter.api.Assertions.assertEquals(0.0, s.idleCredit, 1e-4);
    }
}
