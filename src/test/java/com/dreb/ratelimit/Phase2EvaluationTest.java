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
 * Phase 2.3: Controlled Experimental Evaluation of Refill Mappings and Bucket Interactions.
 *
 * Compares:
 * 1. DREB v1 (Burst-Matched Baseline)
 * 2. DREB v2 (Adaptive Base 1.5 Baseline)
 * 3. DREB-AC (Phase 1 Candidate, BM)
 * 4. Candidate 1: DREB-MAG (Matched Accrual Gain, B=428, gain=1.5R)
 * 5. Candidate 2: DREB-RCA60 (Rebalanced Capacity Allocation, B=600, idleCap=600, gain=1.5R)
 * 6. Candidate 3: DREB-RCA70 (Rebalanced Capacity Allocation, B=700, idleCap=450, gain=1.5R)
 * 7. Candidate 4: DREB-RCA80 (Rebalanced Capacity Allocation, B=800, idleCap=300, gain=1.5R)
 * 8. Token Bucket (C, R)
 * 9. Token Bucket Envelope (9C/7, R)
 * 10. Static Dual Bucket (B = 3C/7, S = 9C/7)
 * 11. Static Dual Bucket (B = 0.70C, S = 9C/7)
 *
 * Evaluates across 9 offered loads: [0.70R, 0.783R, 0.90R, 0.95R, 1.00R, 1.10R, 1.25R, 1.50R, 2.00R]
 * and 8 workload types across 10 random seeds per cell.
 */
@DisplayName("Phase 2.3: Controlled Over-Enforcement Benchmark Suite")
public class Phase2EvaluationTest {

    private static final double C = 1000.0;
    private static final double R = 1000.0;
    private static final double DURATION = 60.0;
    private static final int SEEDS = 10;
    private static final long SEED_START = 3000L;

    public interface LimiterHarness {
        RateLimiter limiter();
        double getCreditSpent();
        ClientState peekState();
    }

    public record ConfigDef(String id, String displayName, java.util.function.Function<Clock, LimiterHarness> factory) {}

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

    private static List<ConfigDef> getConfigurations() {
        List<ConfigDef> list = new ArrayList<>();

        double sMax = 9.0 * C / 7.0; // 1285.71

        // 1. DREB v1 (Burst-Matched)
        list.add(new ConfigDef("dreb_v1_bm", "DREB v1 (BM)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(3.0 * C / 7.0, 5.0 * R / 3.0, sMax, R);
            LinearIdleCreditPolicy linear = new LinearIdleCreditPolicy(2.0 * R / 3.0, 6.0 * C / 7.0, 1.5);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(linear);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 2. DREB v2 (Base 1.5 Default)
        list.add(new ConfigDef("dreb_v2_default", "DREB v2 (default)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(3.0 * C / 7.0, 5.0 * R / 3.0, sMax, R);
            AdaptiveIdleCreditPolicy adaptive = new AdaptiveIdleCreditPolicy(2.0 * R / 3.0, 6.0 * C / 7.0, 1.5, 0.3, 1.0, 1.5, true);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(adaptive);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 3. DREB-AC (Phase 1 Baseline BM, B=428, idleCap=857, gain=666)
        list.add(new ConfigDef("dreb_ac_bm", "DREB-AC (BM Baseline)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(3.0 * C / 7.0, 5.0 * R / 3.0, sMax, R);
            AdaptiveCeilingIdleCreditPolicy ac = new AdaptiveCeilingIdleCreditPolicy(2.0 * R / 3.0, 6.0 * C / 7.0, 1.5, 0.75, 0.05);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(ac);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 4. Candidate 1: DREB-MAG (Matched Gain, B=428, idleCap=857, gain=1.5R)
        list.add(new ConfigDef("dreb_mag", "DREB-MAG (gain=1.5R)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(3.0 * C / 7.0, 5.0 * R / 3.0, sMax, R);
            AdaptiveCeilingIdleCreditPolicy ac = new AdaptiveCeilingIdleCreditPolicy(1.5 * R, 6.0 * C / 7.0, 1.5, 0.75, 0.05);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(ac);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 5. Candidate 2: DREB-RCA60 (B=600, idleCap=600, gain=1.5R)
        list.add(new ConfigDef("dreb_rca60", "DREB-RCA60 (B=600)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(0.60 * C, 5.0 * R / 3.0, sMax, R);
            AdaptiveCeilingIdleCreditPolicy ac = new AdaptiveCeilingIdleCreditPolicy(1.5 * R, 0.60 * C, 1.5, 0.75, 0.05);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(ac);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 6. Candidate 3: DREB-RCA70 (B=700, idleCap=450, gain=1.5R)
        list.add(new ConfigDef("dreb_rca70", "DREB-RCA70 (B=700)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(0.70 * C, 5.0 * R / 3.0, sMax, R);
            AdaptiveCeilingIdleCreditPolicy ac = new AdaptiveCeilingIdleCreditPolicy(1.5 * R, 0.45 * C, 1.5, 0.75, 0.05);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(ac);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 7. Candidate 4: DREB-RCA80 (B=800, idleCap=300, gain=1.5R)
        list.add(new ConfigDef("dreb_rca80", "DREB-RCA80 (B=800)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(0.80 * C, 5.0 * R / 3.0, sMax, R);
            AdaptiveCeilingIdleCreditPolicy ac = new AdaptiveCeilingIdleCreditPolicy(1.5 * R, 0.30 * C, 1.5, 0.75, 0.05);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(ac);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, sMax);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 8. Token Bucket (C, R)
        list.add(new ConfigDef("tb_large", "Token Bucket (C, R)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            TokenBucketLimiter limiter = new TokenBucketLimiter(store, clock, C, R);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return 0.0; }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 9. Token Bucket Envelope (9C/7, R)
        list.add(new ConfigDef("tb_env", "Token Bucket (9C/7, R)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            TokenBucketLimiter limiter = new TokenBucketLimiter(store, clock, sMax, R);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return 0.0; }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 10. Static Dual Bucket (B=3C/7, S=9C/7, zero idle credit)
        list.add(new ConfigDef("static_db_3c7", "Static DB (B=3C/7, S=9C/7)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(3.0 * C / 7.0, 5.0 * R / 3.0, sMax, R);
            LinearIdleCreditPolicy zeroIdle = new LinearIdleCreditPolicy(0.0, 0.0, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, zeroIdle, sMax);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return 0.0; }
                @Override public ClientState peekState() { return limiter.peekState("client"); }
            };
        }));

        // 11. Static Dual Bucket (B=0.70C, S=9C/7, zero idle credit)
        list.add(new ConfigDef("static_db_070c", "Static DB (B=0.70C, S=9C/7)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(0.70 * C, 5.0 * R / 3.0, sMax, R);
            LinearIdleCreditPolicy zeroIdle = new LinearIdleCreditPolicy(0.0, 0.0, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, zeroIdle, sMax);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return 0.0; }
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
    @DisplayName("Execute Phase 2.3 Comprehensive Benchmark Suite")
    void runPhase2Experiments() throws Exception {
        Path outCsv = Paths.get("research/over_enforcement/phase2_experiments_raw.csv");
        Files.createDirectories(outCsv.getParent());

        List<ConfigDef> configs = getConfigurations();
        double[] offeredLoads = {0.70, 0.783, 0.90, 0.95, 1.00, 1.10, 1.25, 1.50, 2.00};

        Map<String, WorkloadGenerator> workloads = new LinkedHashMap<>();

        // 1. Constant-Rate Traffic (unit cost 1)
        workloads.put("constant_rate", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            double dt = 1.0 / (load * R);
            double t = dt;
            while (t < duration) {
                list.add(new Request(t, 1));
                t += dt;
            }
            return list;
        });

        // 2. Poisson Arrivals (mean request cost 20)
        workloads.put("poisson_arrivals", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            Random rng = new Random(seed);
            double meanGap = 20.0 / (load * R);
            double t = 0.0;
            while (t < duration) {
                t += -Math.log(1.0 - rng.nextDouble()) * meanGap;
                if (t >= duration) break;
                int cost = Math.max(1, (int) Math.round(20 + rng.nextGaussian() * 5));
                list.add(new Request(t, cost));
            }
            return list;
        });

        // 3. Bursty ON/OFF Traffic (idle Exp(3*tau), burst in [0.3C, 0.9C])
        workloads.put("bursty_on_off", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            Random rng = new Random(seed);
            double tau = (6.0 * C / 7.0) / (2.0 * R / 3.0); // 1.2857s
            double meanBurst = 0.60 * C; // 600
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

        // 4. Mixed Request Costs (80% small cost 10, 20% large cost 200)
        workloads.put("mixed_costs", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            Random rng = new Random(seed);
            double meanCost = 0.80 * 10 + 0.20 * 200; // 48 tokens
            double meanGap = meanCost / (load * R);
            double t = 0.0;
            while (t < duration) {
                t += -Math.log(1.0 - rng.nextDouble()) * meanGap;
                if (t >= duration) break;
                int cost = rng.nextDouble() < 0.80 ? 10 : 200;
                list.add(new Request(t, cost));
            }
            return list;
        });

        // 5. Near-Capacity Traffic with Periodic Bursts (background 0.80*load, periodic burst 0.50C)
        workloads.put("near_capacity_periodic", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            Random rng = new Random(seed);
            double bgRate = 0.80 * load * R;
            double meanGap = 20.0 / bgRate;
            double t = 0.0;
            double nextBurst = 5.0;
            while (t < duration) {
                t += -Math.log(1.0 - rng.nextDouble()) * meanGap;
                if (t >= duration) break;
                list.add(new Request(t, 20));
                if (t >= nextBurst) {
                    list.add(new Request(t, (int) (0.50 * C)));
                    nextBurst += 5.0;
                }
            }
            return list;
        });

        // 6. Overload Chunked Abuser (Table 2.2 setting: burst 0.95C every 0.95C / (load*R))
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

        // 7. Adversarial Periodic Credit Farming (wait tau, burst C)
        workloads.put("adversarial_farming", (load, seed, duration) -> {
            List<Request> list = new ArrayList<>();
            double tau = 1.2857;
            double tFill = (3.0 * C / 7.0) / (5.0 * R / 3.0); // 0.2571
            double period = tFill + tau;
            double t = period;
            while (t < duration) {
                list.add(new Request(t, (int) C));
                t += period;
            }
            return list;
        });

        try (PrintWriter pw = new PrintWriter(new FileWriter(outCsv.toFile()))) {
            pw.println("config_id,display_name,workload_type,offered_load,seed," +
                    "offered_tokens,admitted_tokens,admitted_rate_norm,frr_percent," +
                    "credit_tokens_drawn,credit_rate_norm,final_k_score");

            for (ConfigDef cfg : configs) {
                for (String wName : workloads.keySet()) {
                    WorkloadGenerator wGen = workloads.get(wName);

                    for (double load : offeredLoads) {
                        // Skip irrelevant combinations for special workloads
                        if ("adversarial_farming".equals(wName) && load != 1.00) continue;

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

                                // Chunked admission to match real-world client burst splitting
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

                            ClientState st = harness.peekState();
                            double finalK = st != null ? st.abuseScore : 0.0;

                            pw.printf("%s,\"%s\",%s,%.3f,%d,%d,%d,%.6f,%.2f,%.2f,%.6f,%.4f%n",
                                    cfg.id, cfg.displayName, wName, load, seed,
                                    offered, admitted, normRate, frr,
                                    creditDrawn, creditRateNorm, finalK);
                        }
                    }
                }
            }
        }

        assertTrue(Files.exists(outCsv));
    }
}
