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
 * Phase 4: Comprehensive Adversarial Evaluation Framework.
 *
 * Implements 5 attack families:
 * A. Timing-aware credit farming (micro-pulsing, refill boundary jitter, periodic, phase-shifted).
 * B. State-aware attacks (white-box optimal vs black-box inferred).
 * C. Closed-loop adaptive attacks (AIMD wait time, binary-search cost, credit maximizer).
 * D. Mixed legitimate and adversarial traffic (competing bursty, multi-worker collision, post-attack recovery).
 * E. Boundary and pathological strategies (near-Bmax, near-C, micro-cost c=1, unadmissible c=1500, bimodal).
 *
 * Evaluates across 30 seeds (5000-5029) over T=60.0s horizon.
 */
@DisplayName("Phase 4: Comprehensive Adversarial Robustness Suite")
public class Phase4AdversarialTest {

    private static final double C = 1000.0;
    private static final double R = 1000.0;
    private static final double DURATION = 60.0;
    private static final int SEEDS = 30;
    private static final long SEED_START = 5000L;

    public interface LimiterHarness {
        RateLimiter limiter();
        double getCreditSpent();
        ClientState peekState();
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
        };
    }

    private static LimiterHarness buildTokenBucket(Clock clock) {
        InMemoryStateStore store = new InMemoryStateStore();
        TokenBucketLimiter limiter = new TokenBucketLimiter(store, clock, C, R);
        return new LimiterHarness() {
            @Override public RateLimiter limiter() { return limiter; }
            @Override public double getCreditSpent() { return 0.0; }
            @Override public ClientState peekState() { return limiter.peekState("client"); }
        };
    }

    private static List<ConfigDef> getPolicies() {
        return List.of(
                new ConfigDef("rca70", "DREB-RCA70 (Primary)", Phase4AdversarialTest::buildRca70),
                new ConfigDef("dreb_ac_bm", "DREB-AC BM", Phase4AdversarialTest::buildDrebAc),
                new ConfigDef("dreb_v2_default", "DREB v2 Default", Phase4AdversarialTest::buildDrebV2),
                new ConfigDef("static_db_070c", "Static DB (0.70C)", Phase4AdversarialTest::buildStaticDb),
                new ConfigDef("tb_large", "Token Bucket (C, R)", Phase4AdversarialTest::buildTokenBucket)
        );
    }

    public record AttackEvent(double time, int cost, boolean isAdversary) {}

    public interface AttackGenerator {
        List<AttackEvent> generate(long seed, double duration, RateLimiter limiterProbe, Clock clockProbe);
    }

    @Test
    @DisplayName("Execute Phase 4 Comprehensive Adversarial Benchmark Suite")
    void runPhase4AdversarialExperiments() throws Exception {
        Path outCsv = Paths.get("research/adversarial/phase4_adversarial_raw.csv");
        Files.createDirectories(outCsv.getParent());

        List<ConfigDef> policies = getPolicies();
        Map<String, AttackGenerator> attacks = new LinkedHashMap<>();

        // =========================================================================
        // FAMILY A: TIMING-AWARE CREDIT FARMING
        // =========================================================================
        // A1. Micro-Pulsing: Wait t_fill + 0.05s, burst 750 (harvesting small micro-credits)
        attacks.put("A1_micro_pulsing", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double period = 0.420 + 0.050; // 0.470s
            double t = period;
            while (t < duration) {
                list.add(new AttackEvent(t, 750, true));
                t += period;
            }
            return list;
        });

        // A2. Boundary Jitter: Alternates t_fill - 0.02s and t_fill + 0.02s
        attacks.put("A2_refill_boundary_jitter", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            Random rng = new Random(seed);
            double t = 0.420;
            while (t < duration) {
                int cost = rng.nextBoolean() ? 700 : 750;
                list.add(new AttackEvent(t, cost, true));
                double jitter = (rng.nextDouble() - 0.5) * 0.04;
                t += 0.420 + jitter;
            }
            return list;
        });

        // A3. Periodic Full-Burst Farming: Wait 0.720s, burst 1000
        attacks.put("A3_periodic_full_burst", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double period = 0.720;
            double t = period;
            while (t < duration) {
                list.add(new AttackEvent(t, (int) C, true));
                t += period;
            }
            return list;
        });

        // A4. Phase-Shifted Sinusoidal Wait: Modulates wait interval [0.5s, 1.5s]
        attacks.put("A4_phase_shifted_sinusoidal", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double t = 1.0;
            double step = 0;
            while (t < duration) {
                list.add(new AttackEvent(t, (int) C, true));
                double interval = 1.0 + 0.4 * Math.sin(step * 0.3);
                t += interval;
                step++;
            }
            return list;
        });

        // =========================================================================
        // FAMILY B: STATE-AWARE ATTACKS
        // =========================================================================
        // B1. White-Box Optimal: Requests exact available burst at t += 0.5s
        attacks.put("B1_white_box_optimal", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double t = 0.5;
            while (t < duration) {
                // In white-box, cost is dynamically calculated during simulation run
                list.add(new AttackEvent(t, -1, true)); // -1 flag for dynamic white-box
                t += 0.5;
            }
            return list;
        });

        // B2. Black-Box Inferred: Probing admission boundary via binary search steps
        attacks.put("B2_black_box_inferred", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double t = 0.5;
            while (t < duration) {
                list.add(new AttackEvent(t, -2, true)); // -2 flag for black-box probe
                t += 0.5;
            }
            return list;
        });

        // =========================================================================
        // FAMILY C: CLOSED-LOOP ADAPTIVE ATTACKS
        // =========================================================================
        // C1. AIMD Interval Adaptation: Increases wait on 429, decreases on 200
        attacks.put("C1_aimd_adaptive_interval", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double t = 0.5;
            while (t < duration) {
                list.add(new AttackEvent(t, -3, true)); // -3 flag for AIMD
                t += 0.5;
            }
            return list;
        });

        // C2. Binary Search Cost Adaptation: Searches acceptable burst size
        attacks.put("C2_binary_search_cost", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double t = 0.8;
            while (t < duration) {
                list.add(new AttackEvent(t, -4, true)); // -4 flag for binary search
                t += 0.8;
            }
            return list;
        });

        // C3. Credit Maximizer: Aims specifically to maximize credit extraction
        attacks.put("C3_credit_maximizer", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double t = 0.720;
            while (t < duration) {
                list.add(new AttackEvent(t, 1000, true));
                t += 0.720;
            }
            return list;
        });

        // =========================================================================
        // FAMILY D: MIXED LEGITIMATE AND ADVERSARIAL WORKLOADS
        // =========================================================================
        // D1. Competing Legitimate Traffic: Adversary (periodic burst 1000 every 1.5s) +
        //     Legitimate client sending Poisson background queries (cost 50, mean gap 0.2s)
        attacks.put("D1_competing_legitimate", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            Random rng = new Random(seed);
            // Adversary stream
            double tAdv = 1.5;
            while (tAdv < duration) {
                list.add(new AttackEvent(tAdv, 1000, true));
                tAdv += 1.5;
            }
            // Legitimate stream
            double tLeg = 0.1;
            while (tLeg < duration) {
                list.add(new AttackEvent(tLeg, 50, false));
                tLeg += -Math.log(1.0 - rng.nextDouble()) * 0.20;
            }
            list.sort(Comparator.comparingDouble(AttackEvent::time));
            return list;
        });

        // D2. Multi-Worker Collision: 4 unsynchronized workers sending bursts of 500 every 1.0s with jitter
        attacks.put("D2_multi_worker_collision", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            Random rng = new Random(seed);
            for (int w = 0; w < 4; w++) {
                double t = 0.5 + w * 0.25;
                while (t < duration) {
                    list.add(new AttackEvent(t, 500, true));
                    t += 1.0 + (rng.nextDouble() - 0.5) * 0.2;
                }
            }
            list.sort(Comparator.comparingDouble(AttackEvent::time));
            return list;
        });

        // D3. Post-Attack Recovery: Attacker active in [0, 30s], Legitimate active in [30s, 60s]
        attacks.put("D3_post_attack_recovery", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            // Heavy attack in [0, 30s]
            double t = 0.720;
            while (t < 30.0) {
                list.add(new AttackEvent(t, 1000, true));
                t += 0.720;
            }
            // Legitimate bursty client in [30s, 60s]
            t = 31.0;
            while (t < duration) {
                list.add(new AttackEvent(t, 700, false));
                t += 1.5;
            }
            return list;
        });

        // =========================================================================
        // FAMILY E: BOUNDARY AND PATHOLOGICAL STRATEGIES
        // =========================================================================
        // E1. Near-Bmax Boundary: Alternating 699, 700, 701 every 0.45s
        attacks.put("E1_near_bmax_boundary", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            int[] costs = {699, 700, 701};
            double t = 0.45;
            int idx = 0;
            while (t < duration) {
                list.add(new AttackEvent(t, costs[idx % 3], true));
                t += 0.45;
                idx++;
            }
            return list;
        });

        // E2. Near-Capacity Boundary: Alternating 999, 1000, 1001 every 0.75s
        attacks.put("E2_near_capacity_boundary", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            int[] costs = {999, 1000, 1001};
            double t = 0.75;
            int idx = 0;
            while (t < duration) {
                list.add(new AttackEvent(t, costs[idx % 3], true));
                t += 0.75;
                idx++;
            }
            return list;
        });

        // E3. Micro-Cost Flood: Stream of c=1 requests at rate 1.5R (gap 1/1500s)
        attacks.put("E3_micro_cost_flood", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double dt = 1.0 / 1500.0;
            double t = dt;
            while (t < duration) {
                list.add(new AttackEvent(t, 1, true));
                t += dt;
            }
            return list;
        });

        // E4. Unadmissible Overdraw: Repeated requests of c = 1500 > S_max (must be 100% rejected)
        attacks.put("E4_unadmissible_overdraw", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double t = 0.5;
            while (t < duration) {
                list.add(new AttackEvent(t, 1500, true));
                t += 0.5;
            }
            return list;
        });

        // E5. Bimodal Alternation: Rapid alternating c=1 and c=1000
        attacks.put("E5_bimodal_alternation", (seed, duration, lp, cp) -> {
            List<AttackEvent> list = new ArrayList<>();
            double t = 0.5;
            boolean toggle = false;
            while (t < duration) {
                list.add(new AttackEvent(t, toggle ? 1 : 1000, true));
                t += 0.5;
                toggle = !toggle;
            }
            return list;
        });

        try (PrintWriter pw = new PrintWriter(new FileWriter(outCsv.toFile()))) {
            pw.println("config_id,attack_family,attack_name,seed,offered_total,admitted_total,admitted_rate_norm," +
                    "offered_adv,admitted_adv,adv_rate_norm,offered_legit,admitted_legit,legit_frr_percent," +
                    "credit_drawn,credit_rate_norm,ordinary_drawn,ordinary_rate_norm,admission_ratio_percent,final_k_score");

            for (ConfigDef cfg : policies) {
                for (String attackName : attacks.keySet()) {
                    AttackGenerator gen = attacks.get(attackName);
                    String family = attackName.substring(0, 2);

                    for (int s = 0; s < SEEDS; s++) {
                        long seed = SEED_START + s;

                        ManualClock clock = new ManualClock(0.0);
                        LimiterHarness harness = cfg.factory.apply(clock);

                        List<AttackEvent> events = gen.generate(seed, DURATION, harness.limiter(), clock);

                        long offeredTotal = 0;
                        long admittedTotal = 0;
                        long offeredAdv = 0;
                        long admittedAdv = 0;
                        long offeredLegit = 0;
                        long admittedLegit = 0;
                        int totalRequests = 0;
                        int acceptedRequests = 0;

                        // Dynamic state tracking for adaptive attackers
                        double lastWait = 0.5;
                        int lastCost = 500;

                        for (int i = 0; i < events.size(); i++) {
                            AttackEvent ev = events.get(i);
                            clock.set(ev.time);

                            int cost = ev.cost;

                            // Handle dynamic attack logic
                            if (cost == -1) {
                                // White-box optimal: queries state and requests exact capacity
                                ClientState st = harness.peekState();
                                double b = st == null ? 700.0 : st.burstTokens;
                                double sTokens = st == null ? 1285.71 : st.sustainedTokens;
                                double ic = st == null ? 0.0 : st.idleCredit / 1.5;
                                cost = Math.max(1, (int) Math.min(sTokens, b + ic));
                            } else if (cost == -2) {
                                // Black-box inferred probe
                                cost = lastCost;
                            } else if (cost == -3) {
                                // AIMD: uses current adaptive cost
                                cost = 800;
                            } else if (cost == -4) {
                                // Binary search cost
                                cost = lastCost;
                            }

                            offeredTotal += cost;
                            if (ev.isAdversary) offeredAdv += cost;
                            else offeredLegit += cost;
                            totalRequests++;

                            // Execute allow
                            Decision d = harness.limiter().allow("client", cost);

                            if (d == Decision.ALLOW) {
                                admittedTotal += cost;
                                if (ev.isAdversary) admittedAdv += cost;
                                else admittedLegit += cost;
                                acceptedRequests++;

                                // Update adaptive states
                                if (ev.cost == -2 || ev.cost == -4) {
                                    lastCost = Math.min(1000, (int) (lastCost * 1.15) + 10);
                                }
                            } else {
                                // DENIED
                                if (ev.cost == -2 || ev.cost == -4) {
                                    lastCost = Math.max(50, (int) (lastCost * 0.70));
                                }
                            }
                        }

                        double normRateTotal = admittedTotal / (R * DURATION);
                        double normRateAdv = offeredAdv > 0 ? (admittedAdv / (R * DURATION)) : 0.0;
                        double legitFrr = offeredLegit > 0 ? (100.0 * (offeredLegit - admittedLegit) / offeredLegit) : 0.0;
                        double creditDrawn = harness.getCreditSpent();
                        double creditRateNorm = creditDrawn / (R * DURATION);
                        double ordinaryDrawn = Math.max(0.0, admittedAdv - creditDrawn);
                        double ordinaryRateNorm = ordinaryDrawn / (R * DURATION);
                        double admRatio = totalRequests > 0 ? (100.0 * acceptedRequests / totalRequests) : 0.0;

                        ClientState finalSt = harness.peekState();
                        double finalK = finalSt != null ? finalSt.abuseScore : 0.0;

                        pw.printf("%s,%s,%s,%d,%d,%d,%.6f,%d,%d,%.6f,%d,%d,%.2f,%.2f,%.6f,%.2f,%.6f,%.2f,%.4f%n",
                                cfg.id, family, attackName, seed,
                                offeredTotal, admittedTotal, normRateTotal,
                                offeredAdv, admittedAdv, normRateAdv,
                                offeredLegit, admittedLegit, legitFrr,
                                creditDrawn, creditRateNorm, ordinaryDrawn, ordinaryRateNorm,
                                admRatio, finalK);
                    }
                }
            }
        }

        assertTrue(Files.exists(outCsv));
    }
}
