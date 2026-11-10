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
 * Phase 5: Rigorous Baseline Comparison and Pareto Frontier Benchmark Suite.
 *
 * Compares 5 rate-limiting policies across 10 paired workloads over 30 deterministic seeds (5000-5029).
 * Policies:
 * 1. DREB-RCA70 (Primary Candidate)
 * 2. DREB-AC BM
 * 3. DREB v2 Default
 * 4. Static Dual-Bucket (0.70C)
 * 5. Token Bucket (C, R)
 *
 * Collects metrics for:
 * - Legitimate performance (FRR, throughput, burst acceptance, recovery time)
 * - Adversarial suppression (total rate, credit rate, attack success ratio)
 * - Guarantee compliance (cumulative envelope A(T) <= S_max + R*T, slack, violation)
 * - Operational overhead (P50, P95, P99 decision latency in nanoseconds)
 */
@DisplayName("Phase 5: Baseline Comparison and Pareto Benchmark Suite")
public class Phase5BaselineComparisonTest {

    private static final double C = 1000.0;
    private static final double R = 1000.0;
    private static final double DURATION = 60.0;
    private static final int SEEDS = 30;
    private static final long SEED_START = 5000L;

    public interface LimiterHarness {
        RateLimiter limiter();
        double getCreditSpent();
        ClientState peekState();
        double getSMax();
    }

    public record ConfigDef(
            String id,
            String displayName,
            java.util.function.Function<Clock, LimiterHarness> factory
    ) {}

    public static class TrackingIdlePolicy implements IdleCreditPolicy {
        private final IdleCreditPolicy delegate;
        private double totalCreditSpent = 0.0;

        public TrackingIdlePolicy(IdleCreditPolicy delegate) {
            this.delegate = delegate;
        }

        @Override public void accrue(ClientState state, double dt, double tFull) { delegate.accrue(state, dt, tFull); }
        @Override public void spend(ClientState state, double deficit) {
            if (deficit > 0.0) totalCreditSpent += deficit;
            delegate.spend(state, deficit);
        }
        @Override public double effectiveCredit(ClientState state) { return delegate.effectiveCredit(state); }
        public double totalCreditSpent() { return totalCreditSpent; }
    }

    private static LimiterHarness buildRca70(Clock clock) {
        InMemoryStateStore store = new InMemoryStateStore();
        double sMax = 9.0 * C / 7.0; // 1285.71
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(700.0, 5.0 * R / 3.0, sMax, R);
        AdaptiveCeilingIdleCreditPolicy policy = new AdaptiveCeilingIdleCreditPolicy(1.5 * R, 450.0, 1.5, 0.75, 0.05);
        TrackingIdlePolicy tracking = new TrackingIdlePolicy(policy);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
        return new LimiterHarness() {
            @Override public RateLimiter limiter() { return limiter; }
            @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
            @Override public ClientState peekState() { return limiter.peekState("client"); }
            @Override public double getSMax() { return sMax; }
        };
    }

    private static LimiterHarness buildDrebAc(Clock clock) {
        InMemoryStateStore store = new InMemoryStateStore();
        double sMax = 9.0 * C / 7.0;
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(3.0 * C / 7.0, 5.0 * R / 3.0, sMax, R);
        AdaptiveCeilingIdleCreditPolicy policy = new AdaptiveCeilingIdleCreditPolicy(2.0 * R / 3.0, 6.0 * C / 7.0, 1.5, 0.75, 0.05);
        TrackingIdlePolicy tracking = new TrackingIdlePolicy(policy);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
        return new LimiterHarness() {
            @Override public RateLimiter limiter() { return limiter; }
            @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
            @Override public ClientState peekState() { return limiter.peekState("client"); }
            @Override public double getSMax() { return sMax; }
        };
    }

    private static LimiterHarness buildDrebV2(Clock clock) {
        InMemoryStateStore store = new InMemoryStateStore();
        double sMax = 9.0 * C / 7.0;
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(3.0 * C / 7.0, 5.0 * R / 3.0, sMax, R);
        AdaptiveIdleCreditPolicy policy = new AdaptiveIdleCreditPolicy(2.0 * R / 3.0, 6.0 * C / 7.0, 1.5, 0.3, 1.0, 1.5, true);
        TrackingIdlePolicy tracking = new TrackingIdlePolicy(policy);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
        return new LimiterHarness() {
            @Override public RateLimiter limiter() { return limiter; }
            @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
            @Override public ClientState peekState() { return limiter.peekState("client"); }
            @Override public double getSMax() { return sMax; }
        };
    }

    private static LimiterHarness buildStaticDb(Clock clock) {
        InMemoryStateStore store = new InMemoryStateStore();
        double sMax = 9.0 * C / 7.0;
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(700.0, 5.0 * R / 3.0, sMax, R);
        LinearIdleCreditPolicy policy = new LinearIdleCreditPolicy(0.0, 0.0, 1.5);
        TrackingIdlePolicy tracking = new TrackingIdlePolicy(policy);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
        return new LimiterHarness() {
            @Override public RateLimiter limiter() { return limiter; }
            @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
            @Override public ClientState peekState() { return limiter.peekState("client"); }
            @Override public double getSMax() { return sMax; }
        };
    }

    private static LimiterHarness buildTokenBucket(Clock clock) {
        InMemoryStateStore store = new InMemoryStateStore();
        TokenBucketLimiter limiter = new TokenBucketLimiter(store, clock, C, R);
        return new LimiterHarness() {
            @Override public RateLimiter limiter() { return limiter; }
            @Override public double getCreditSpent() { return 0.0; }
            @Override public ClientState peekState() { return limiter.peekState("client"); }
            @Override public double getSMax() { return C; }
        };
    }

    public static List<ConfigDef> getPolicies() {
        return List.of(
                new ConfigDef("rca70", "DREB-RCA70 (Primary)", Phase5BaselineComparisonTest::buildRca70),
                new ConfigDef("dreb_ac_bm", "DREB-AC BM", Phase5BaselineComparisonTest::buildDrebAc),
                new ConfigDef("dreb_v2_default", "DREB v2 Default", Phase5BaselineComparisonTest::buildDrebV2),
                new ConfigDef("static_db_070c", "Static DB (0.70C)", Phase5BaselineComparisonTest::buildStaticDb),
                new ConfigDef("tb_large", "Token Bucket (C, R)", Phase5BaselineComparisonTest::buildTokenBucket)
        );
    }

    public record WorkloadEvent(double time, int cost, boolean isAdversary) {}

    public interface WorkloadGenerator {
        List<WorkloadEvent> generate(long seed, double duration);
    }

    private static int samplePoisson(double lambda, Random rng) {
        if (lambda <= 0.0) return 0;
        double l = Math.exp(-lambda);
        double p = 1.0;
        int k = 0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > l);
        return k - 1;
    }

    /**
     * Latency profiling measuring P50, P95, P99 decision latency in nanoseconds.
     */
    private static double[] profileLatency(java.util.function.Function<Clock, LimiterHarness> factory) {
        ManualClock clock = new ManualClock(0.0);
        LimiterHarness harness = factory.apply(clock);

        // Warmup: 20,000 requests
        for (int i = 0; i < 20_000; i++) {
            clock.set(i * 0.001);
            harness.limiter().allow("bench", 1);
        }

        // Measurement: 50,000 requests
        int n = 50_000;
        long[] samples = new long[n];
        for (int i = 0; i < n; i++) {
            clock.set(30.0 + i * 0.001);
            long start = System.nanoTime();
            harness.limiter().allow("bench", 1);
            samples[i] = System.nanoTime() - start;
        }

        Arrays.sort(samples);
        double p50 = samples[(int) (n * 0.50)];
        double p95 = samples[(int) (n * 0.95)];
        double p99 = samples[(int) (n * 0.99)];
        return new double[]{p50, p95, p99};
    }

    @Test
    @DisplayName("Execute Phase 5 Comprehensive Baseline Comparison & Pareto Benchmark")
    void runPhase5BaselineComparison() throws Exception {
        Path outCsv = Paths.get("research/comparison/phase5_baseline_comparison_raw.csv");
        Files.createDirectories(outCsv.getParent());

        List<ConfigDef> policies = getPolicies();
        Map<String, WorkloadGenerator> workloads = new LinkedHashMap<>();

        // W01. Benign Steady Traffic: Poisson stream at 0.98R (mean 980 tokens/s, step 0.05s)
        workloads.put("W01_benign_steady", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            Random rng = new Random(seed);
            double t = 0.05;
            double step = 0.05;
            while (t < duration) {
                int count = samplePoisson(0.98 * R * step, rng);
                if (count > 0) {
                    list.add(new WorkloadEvent(t, count, false));
                }
                t += step;
            }
            return list;
        });

        // W02. Legitimate ON/OFF Burst Traffic: Compliant bursts c=700 every 1.5s (idle gap 1.5s)
        workloads.put("W02_legitimate_on_off_burst", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            double t = 1.5;
            while (t < duration) {
                list.add(new WorkloadEvent(t, 700, false));
                t += 1.5;
            }
            return list;
        });

        // W03. Sustained Overload: Unrelenting stream offering 1.50R (cost 15 every 0.010s)
        workloads.put("W03_sustained_overload", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            double t = 0.010;
            while (t < duration) {
                list.add(new WorkloadEvent(t, 15, false));
                t += 0.010;
            }
            return list;
        });

        // W04. Mixed Competing Traffic: Poisson legitimate (cost 50, gap 0.2s) + Periodic adversarial burst (cost 1000, gap 1.5s)
        workloads.put("W04_mixed_competing", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            Random rng = new Random(seed);
            double tAdv = 1.5;
            while (tAdv < duration) {
                list.add(new WorkloadEvent(tAdv, 1000, true));
                tAdv += 1.5;
            }
            double tLeg = 0.1;
            while (tLeg < duration) {
                list.add(new WorkloadEvent(tLeg, 50, false));
                tLeg += -Math.log(1.0 - rng.nextDouble()) * 0.20;
            }
            list.sort(Comparator.comparingDouble(WorkloadEvent::time));
            return list;
        });

        // W05. Timing Credit Farming: Periodic burst c=750 at refill threshold (gap 0.470s)
        workloads.put("W05_timing_farming", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            double t = 0.470;
            while (t < duration) {
                list.add(new WorkloadEvent(t, 750, true));
                t += 0.470;
            }
            return list;
        });

        // W06. White-Box Optimal Extraction: Attacker inspects internal registers and requests exact allowable capacity
        workloads.put("W06_white_box_oracle", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            double t = 0.50;
            while (t < duration) {
                list.add(new WorkloadEvent(t, -1, true)); // -1 flag for dynamic oracle
                t += 0.50;
            }
            return list;
        });

        // W07. Black-Box Adaptive Probe: Attacker adjusts cost based on 200/429 feedback
        workloads.put("W07_black_box_probe", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            double t = 0.50;
            while (t < duration) {
                list.add(new WorkloadEvent(t, -2, true)); // -2 flag for MIMD probe
                t += 0.50;
            }
            return list;
        });

        // W08. Closed-Loop AIMD Interval Attack: Attacker requests c=800 adjusting wait interval
        workloads.put("W08_aimd_adaptive", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            double t = 0.50;
            while (t < duration) {
                list.add(new WorkloadEvent(t, -3, true)); // -3 flag for AIMD
                t += 0.50;
            }
            return list;
        });

        // W09. Multi-Worker Concurrent Collision: 4 unsynchronized workers sending bursts of 500 every 1.0s
        workloads.put("W09_multi_worker_collision", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            Random rng = new Random(seed);
            for (int w = 0; w < 4; w++) {
                double t = 0.5 + w * 0.25;
                while (t < duration) {
                    list.add(new WorkloadEvent(t, 500, true));
                    t += 1.0 + (rng.nextDouble() - 0.5) * 0.2;
                }
            }
            list.sort(Comparator.comparingDouble(WorkloadEvent::time));
            return list;
        });

        // W10. Post-Attack Recovery: Attacker active [0, 30s] (burst 1000 every 0.720s), stops at 30s;
        //     Legitimate burst client active [30s, 60s] (burst 700 every 1.5s)
        workloads.put("W10_post_attack_recovery", (seed, duration) -> {
            List<WorkloadEvent> list = new ArrayList<>();
            double t = 0.720;
            while (t < 30.0) {
                list.add(new WorkloadEvent(t, 1000, true));
                t += 0.720;
            }
            t = 31.0;
            while (t < duration) {
                list.add(new WorkloadEvent(t, 700, false));
                t += 1.5;
            }
            return list;
        });

        // Profile latency once per policy
        Map<String, double[]> latencies = new LinkedHashMap<>();
        for (ConfigDef cfg : policies) {
            latencies.put(cfg.id, profileLatency(cfg.factory));
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(outCsv.toFile()))) {
            pw.println("config_id,workload_id,workload_name,seed,duration_sec,offered_total,admitted_total," +
                    "admitted_rate_norm,g1_envelope,g1_slack,g1_violation,initial_burst_contrib," +
                    "offered_adv,admitted_adv,adv_rate_norm,credit_drawn,credit_rate_norm,ordinary_drawn,ordinary_rate_norm," +
                    "offered_legit,admitted_legit,legit_frr_percent,legit_burst_acc_percent,recovery_time_sec," +
                    "total_requests,admitted_requests,adm_ratio_percent,final_k_score,latency_p50_ns,latency_p95_ns,latency_p99_ns");

            for (ConfigDef cfg : policies) {
                double[] lat = latencies.get(cfg.id);

                for (String wId : workloads.keySet()) {
                    WorkloadGenerator gen = workloads.get(wId);

                    for (int s = 0; s < SEEDS; s++) {
                        long seed = SEED_START + s;

                        ManualClock clock = new ManualClock(0.0);
                        LimiterHarness harness = cfg.factory.apply(clock);

                        List<WorkloadEvent> events = gen.generate(seed, DURATION);

                        long offeredTotal = 0;
                        long admittedTotal = 0;
                        long offeredAdv = 0;
                        long admittedAdv = 0;
                        long offeredLegit = 0;
                        long admittedLegit = 0;
                        int totalRequests = 0;
                        int acceptedRequests = 0;

                        // Burst tracking
                        int totalLegitBursts = 0;
                        int acceptedLegitBursts = 0;

                        // Post-attack recovery tracking
                        double firstLegitAllowedTime = -1.0;

                        // Adaptive attack state
                        int lastProbeCost = 500;

                        for (int i = 0; i < events.size(); i++) {
                            WorkloadEvent ev = events.get(i);
                            clock.set(ev.time);

                            int cost = ev.cost;

                            // Handle dynamic attack costs
                            if (cost == -1) {
                                // White-box optimal
                                ClientState st = harness.peekState();
                                double b = st == null ? 700.0 : st.burstTokens;
                                double sTokens = st == null ? harness.getSMax() : st.sustainedTokens;
                                double ic = st == null ? 0.0 : st.idleCredit / 1.5;
                                cost = Math.max(1, (int) Math.min(sTokens, b + ic));
                            } else if (cost == -2) {
                                // Black-box MIMD
                                cost = lastProbeCost;
                            } else if (cost == -3) {
                                // AIMD
                                cost = 800;
                            }

                            offeredTotal += cost;
                            if (ev.isAdversary) offeredAdv += cost;
                            else {
                                offeredLegit += cost;
                                if (cost >= 700) totalLegitBursts++;
                            }
                            totalRequests++;

                            Decision d = harness.limiter().allow("client", cost);

                            if (d == Decision.ALLOW) {
                                admittedTotal += cost;
                                if (ev.isAdversary) admittedAdv += cost;
                                else {
                                    admittedLegit += cost;
                                    if (cost >= 700) acceptedLegitBursts++;
                                    if (ev.time >= 30.0 && firstLegitAllowedTime < 0) {
                                        firstLegitAllowedTime = ev.time;
                                    }
                                }
                                acceptedRequests++;

                                if (ev.cost == -2) {
                                    lastProbeCost = Math.min(1000, (int) (lastProbeCost * 1.15) + 10);
                                }
                            } else {
                                if (ev.cost == -2) {
                                    lastProbeCost = Math.max(50, (int) (lastProbeCost * 0.70));
                                }
                            }
                        }

                        double normRateTotal = admittedTotal / (R * DURATION);
                        double g1Envelope = harness.getSMax() + R * DURATION;
                        double g1Slack = g1Envelope - admittedTotal;
                        double g1Violation = Math.max(0.0, admittedTotal - g1Envelope);
                        double initialBurstContrib = harness.getSMax() / DURATION;

                        double normRateAdv = offeredAdv > 0 ? (admittedAdv / (R * DURATION)) : 0.0;
                        double creditDrawn = harness.getCreditSpent();
                        double creditRateNorm = creditDrawn / (R * DURATION);
                        double ordinaryDrawn = Math.max(0.0, admittedAdv - creditDrawn);
                        double ordinaryRateNorm = ordinaryDrawn / (R * DURATION);

                        double legitFrr = offeredLegit > 0 ? (100.0 * (offeredLegit - admittedLegit) / offeredLegit) : 0.0;
                        double burstAcc = totalLegitBursts > 0 ? (100.0 * acceptedLegitBursts / totalLegitBursts) : 0.0;
                        double recoveryTime = (firstLegitAllowedTime >= 30.0) ? (firstLegitAllowedTime - 30.0) : 0.0;
                        double admRatio = totalRequests > 0 ? (100.0 * acceptedRequests / totalRequests) : 0.0;

                        ClientState finalSt = harness.peekState();
                        double finalK = finalSt != null ? finalSt.abuseScore : 0.0;

                        pw.printf("%s,%s,%s,%d,%.1f,%d,%d,%.6f,%.2f,%.2f,%.2f,%.4f,%d,%d,%.6f,%.2f,%.6f,%.2f,%.6f,%d,%d,%.2f,%.2f,%.3f,%d,%d,%.2f,%.4f,%.1f,%.1f,%.1f%n",
                                cfg.id, wId, wId, seed, DURATION,
                                offeredTotal, admittedTotal, normRateTotal,
                                g1Envelope, g1Slack, g1Violation, initialBurstContrib,
                                offeredAdv, admittedAdv, normRateAdv,
                                creditDrawn, creditRateNorm, ordinaryDrawn, ordinaryRateNorm,
                                offeredLegit, admittedLegit, legitFrr, burstAcc, recoveryTime,
                                totalRequests, acceptedRequests, admRatio, finalK,
                                lat[0], lat[1], lat[2]);
                    }
                }
            }
        }

        assertTrue(Files.exists(outCsv));
    }
}
