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
 * Phase 1.3: Empirical Attack Optimization (Mixed Strategies) & Theorem 2 Validation Sweep.
 *
 * Evaluates:
 * 1. Long recovery + multi-burst
 * 2. Partial recovery sweep (D in [0.1*tau, 3.0*tau])
 * 3. High-frequency micro-pulsing (D in [0.01s, 0.20s])
 * 4. Variable / alternating burst sizes
 *
 * Separately tracks:
 * - Total admitted traffic
 * - Credit-funded portion of admitted traffic (sum of deficits delta_j)
 * - Maximum abuseScore K(t) observed
 * - Final abuseScore K(T)
 * - Comparison against theoretical asymptotic bound (baseCap * lambda_rec = 42.857 tokens/s = 0.04286 R)
 *   and finite-horizon bound (baseCap * lambda_rec + baseCap / T = 50.000 tokens/s = 0.05000 R).
 */
@DisplayName("Phase 1.3: Mixed-Strategy Attack Sweep & Credit Extraction Bound Verification")
public class Phase13SweepTest {

    private static final double C = 1000.0;
    private static final double R = 1000.0;
    private static final double DURATION = 120.0;

    private static final double B_MAX = 3.0 * C / 7.0;       // 428.5714
    private static final double B_RATE = 5.0 * R / 3.0;      // 1666.6667
    private static final double S_MAX = 9.0 * C / 7.0;       // 1285.7143
    private static final double S_RATE = R;                  // 1000.0
    private static final double IDLE_CAP = 6.0 * C / 7.0;    // 857.1429
    private static final double IDLE_GAIN = 2.0 * R / 3.0;   // 666.6667
    private static final double TAU = IDLE_CAP / IDLE_GAIN;  // 1.2857 s
    private static final double T_FILL = B_MAX / B_RATE;     // 0.2571 s

    private static final double THEORETICAL_K_MAX = (1.0 / 0.75) * ((B_RATE / (1.5 * B_MAX * 0.05)) - 1.0) + (1.0 / 1.5); // ~68.47

    public static class TrackingIdlePolicy implements IdleCreditPolicy {
        private final IdleCreditPolicy delegate;
        private double totalCreditSpent = 0.0;

        public TrackingIdlePolicy(IdleCreditPolicy delegate) {
            this.delegate = delegate;
        }

        @Override
        public void accrue(ClientState state, double dt, double tFull) {
            delegate.accrue(state, dt, tFull);
        }

        @Override
        public void spend(ClientState state, double deficit) {
            if (deficit > 0.0) {
                totalCreditSpent += deficit;
            }
            delegate.spend(state, deficit);
        }

        @Override
        public double effectiveCredit(ClientState state) {
            return delegate.effectiveCredit(state);
        }

        public double totalCreditSpent() {
            return totalCreditSpent;
        }

        public IdleCreditPolicy delegate() {
            return delegate;
        }
    }

    public interface LimiterHarness {
        RateLimiter limiter();
        double getCreditSpent();
        ClientState peekState();
    }

    public record PolicyDef(String id, String displayName, java.util.function.Function<Clock, LimiterHarness> factory) {}

    private static List<PolicyDef> getTargetPolicies() {
        List<PolicyDef> list = new ArrayList<>();

        // 1. DREB-AC (beta = 0.75, lambda = 0.05) - Primary candidate
        list.add(new PolicyDef("dreb_ac_b075", "DREB-AC (b=0.75, r=0.05)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            AdaptiveCeilingIdleCreditPolicy ac = new AdaptiveCeilingIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.75, 0.05);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(ac);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, S_MAX);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("adv"); }
            };
        }));

        // 2. DREB-AC (beta = 1.50, lambda = 0.05)
        list.add(new PolicyDef("dreb_ac_b150", "DREB-AC (b=1.50, r=0.05)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            AdaptiveCeilingIdleCreditPolicy ac = new AdaptiveCeilingIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 1.50, 0.05);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(ac);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, S_MAX);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("adv"); }
            };
        }));

        // 3. DREB v1 (burst-matched) - Baseline
        list.add(new PolicyDef("dreb_v1_bm", "DREB v1 (burst-matched)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            LinearIdleCreditPolicy linear = new LinearIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(linear);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, S_MAX);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("adv"); }
            };
        }));

        // 4. DREB v2 (base 1.5, default) - Baseline
        list.add(new PolicyDef("dreb_v2_default", "DREB v2 (default)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            AdaptiveIdleCreditPolicy adaptive = new AdaptiveIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.3, 1.0, 1.5, true);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(adaptive);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, S_MAX);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return tracking.totalCreditSpent(); }
                @Override public ClientState peekState() { return limiter.peekState("adv"); }
            };
        }));

        // 5. Token Bucket (C, R) - Baseline
        list.add(new PolicyDef("token_bucket", "Token Bucket (C, R)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            TokenBucketLimiter limiter = new TokenBucketLimiter(store, clock, C, R);
            return new LimiterHarness() {
                @Override public RateLimiter limiter() { return limiter; }
                @Override public double getCreditSpent() { return 0.0; }
                @Override public ClientState peekState() { return limiter.peekState("adv"); }
            };
        }));

        return list;
    }

    public record SimulationResult(
            String strategyFamily,
            String paramSpec,
            String policyId,
            long offeredTokens,
            long admittedTokens,
            double totalAdmittedRateNorm,
            double creditTokensDrawn,
            double creditRateTokensPerSec,
            double creditRateNorm,
            double maxAbuseScore,
            double finalAbuseScore,
            boolean exceedsAsymptotic,
            boolean exceedsExactTheorem2Bound,
            double exactTheorem2BoundRate
    ) {}

    public interface AttackSchedule {
        List<Request> generateSchedule(double duration);
    }

    public record Request(double time, int cost) {}

    @Test
    @DisplayName("Run Phase 1.3 Mixed-Strategy Sweep and Verify Theorem 2 Bounds")
    void runPhase13Sweep() throws Exception {
        Path outCsv = Paths.get("research/credit_farming/phase1_3_mixed_sweep.csv");
        Files.createDirectories(outCsv.getParent());

        List<PolicyDef> policies = getTargetPolicies();
        List<SimulationResult> results = new ArrayList<>();

        Map<String, AttackSchedule> attacks = new LinkedHashMap<>();

        // 1. Long recovery + multi-burst schedules
        double[] longWaits = {5.0, 10.0, 20.0, 30.0};
        int[] burstCounts = {2, 3, 5, 10};
        double[] burstSpacings = {0.05, 0.1, 0.5};

        for (double dw : longWaits) {
            for (int nb : burstCounts) {
                for (double ds : burstSpacings) {
                    if (dw + nb * ds > DURATION) continue;
                    String spec = String.format("dw=%.1fs,n=%d,ds=%.2fs", dw, nb, ds);
                    attacks.put("long_rec_multiburst:" + spec, duration -> {
                        List<Request> reqs = new ArrayList<>();
                        double t = 0.0;
                        while (t < duration) {
                            t += dw;
                            for (int i = 0; i < nb && t < duration; i++) {
                                reqs.add(new Request(t, (int) C));
                                t += ds;
                            }
                        }
                        return reqs;
                    });
                }
            }
        }

        // 2. Partial recovery sweep (D = T_FILL + alpha * TAU)
        double[] alphas = {0.05, 0.10, 0.25, 0.50, 0.75, 1.00, 1.25, 1.50, 2.00, 2.50, 3.00, 4.00};
        for (double a : alphas) {
            double period = T_FILL + a * TAU;
            String spec = String.format("alpha=%.2f,period=%.3fs", a, period);
            attacks.put("partial_recovery:" + spec, duration -> {
                List<Request> reqs = new ArrayList<>();
                double t = period;
                while (t < duration) {
                    reqs.add(new Request(t, (int) C));
                    t += period;
                }
                return reqs;
            });
        }

        // 3. High-frequency micro-pulsing (D in [0.01s, 0.20s])
        double[] microGaps = {0.01, 0.02, 0.05, 0.10, 0.15, 0.20};
        int[] microCosts = {10, 20, 50, 100, 150, 200, 300, 500};
        for (double gap : microGaps) {
            for (int cost : microCosts) {
                String spec = String.format("gap=%.2fs,cost=%d", gap, cost);
                attacks.put("micro_pulsing:" + spec, duration -> {
                    List<Request> reqs = new ArrayList<>();
                    double t = gap;
                    while (t < duration) {
                        reqs.add(new Request(t, cost));
                        t += gap;
                    }
                    return reqs;
                });
            }
        }

        // 4. Variable / alternating burst size
        attacks.put("variable_burst:alt_0.2C_1.0C_tau", duration -> {
            List<Request> reqs = new ArrayList<>();
            double t = TAU;
            boolean toggle = false;
            while (t < duration) {
                reqs.add(new Request(t, toggle ? (int) (1.0 * C) : (int) (0.2 * C)));
                toggle = !toggle;
                t += TAU;
            }
            return reqs;
        });

        attacks.put("variable_burst:alt_0.5C_1.2C_1.5tau", duration -> {
            List<Request> reqs = new ArrayList<>();
            double t = 1.5 * TAU;
            boolean toggle = false;
            while (t < duration) {
                reqs.add(new Request(t, toggle ? (int) (1.2 * C) : (int) (0.5 * C)));
                toggle = !toggle;
                t += 1.5 * TAU;
            }
            return reqs;
        });

        attacks.put("variable_burst:alt_0.3C_0.8C_0.5tau", duration -> {
            List<Request> reqs = new ArrayList<>();
            double t = 0.5 * TAU;
            boolean toggle = false;
            while (t < duration) {
                reqs.add(new Request(t, toggle ? (int) (0.8 * C) : (int) (0.3 * C)));
                toggle = !toggle;
                t += 0.5 * TAU;
            }
            return reqs;
        });

        attacks.put("variable_burst:mixed_3step_cycle", duration -> {
            List<Request> reqs = new ArrayList<>();
            double t = 0.5 * TAU;
            int step = 0;
            while (t < duration) {
                if (step == 0) {
                    reqs.add(new Request(t, (int) (0.2 * C)));
                    t += 0.5 * TAU;
                } else if (step == 1) {
                    reqs.add(new Request(t, (int) (0.5 * C)));
                    t += 0.5 * TAU;
                } else {
                    reqs.add(new Request(t, (int) (1.0 * C)));
                    t += 1.5 * TAU;
                }
                step = (step + 1) % 3;
            }
            return reqs;
        });

        // Randomized mixed burst sizes and intervals (10 seeds)
        for (int seed = 101; seed <= 110; seed++) {
            final int s = seed;
            attacks.put("variable_burst:random_seed_" + s, duration -> {
                Random rng = new Random(s);
                List<Request> reqs = new ArrayList<>();
                double t = 0.5;
                while (t < duration) {
                    double dt = 0.5 * TAU + rng.nextDouble() * 1.5 * TAU;
                    t += dt;
                    if (t >= duration) break;
                    int cost = (int) (0.2 * C + rng.nextDouble() * 1.0 * C);
                    reqs.add(new Request(t, cost));
                }
                return reqs;
            });
        }

        // Run simulations across all attacks and policies
        try (PrintWriter pw = new PrintWriter(new FileWriter(outCsv.toFile()))) {
            pw.println("strategy_family,param_spec,policy_id,offered_tokens,admitted_tokens,total_admitted_rate_norm," +
                    "credit_tokens_drawn,credit_rate_tokens_per_sec,credit_rate_norm," +
                    "max_abuse_score,final_abuse_score,asymptotic_bound_rate,exact_theorem2_bound_rate," +
                    "exceeds_asymptotic,exceeds_exact_bound");

            for (Map.Entry<String, AttackSchedule> entry : attacks.entrySet()) {
                String fullKey = entry.getKey();
                String strategyFamily = fullKey.substring(0, fullKey.indexOf(':'));
                String paramSpec = fullKey.substring(fullKey.indexOf(':') + 1);

                List<Request> schedule = entry.getValue().generateSchedule(DURATION);

                for (PolicyDef policy : policies) {
                    ManualClock clock = new ManualClock(0.0);
                    LimiterHarness harness = policy.factory.apply(clock);

                    long offered = 0;
                    long admitted = 0;
                    double maxK = 0.0;

                    for (Request req : schedule) {
                        clock.set(req.time);
                        offered += req.cost;
                        Decision dec = harness.limiter().allow("adv", req.cost);
                        if (dec == Decision.ALLOW) {
                            admitted += req.cost;
                        }
                        ClientState state = harness.peekState();
                        if (state != null && state.abuseScore > maxK) {
                            maxK = state.abuseScore;
                        }
                    }

                    double creditDrawn = harness.getCreditSpent();
                    double creditRate = creditDrawn / DURATION;
                    double creditRateNorm = creditDrawn / (R * DURATION);
                    double admRateNorm = admitted / (R * DURATION);

                    ClientState finalState = harness.peekState();
                    double finalK = finalState != null ? finalState.abuseScore : 0.0;

                    double asympBound = IDLE_CAP * 0.05; // 42.8571
                    double exactBound = (IDLE_CAP * (0.05 * DURATION + finalK)) / DURATION;

                    boolean exceedsAsymp = creditRate > (asympBound + 1e-6);
                    boolean exceedsExact = creditRate > (exactBound + 1e-4);

                    SimulationResult res = new SimulationResult(
                            strategyFamily, paramSpec, policy.id,
                            offered, admitted, admRateNorm,
                            creditDrawn, creditRate, creditRateNorm,
                            maxK, finalK, exceedsAsymp, exceedsExact, exactBound
                    );
                    results.add(res);

                    pw.printf("%s,\"%s\",%s,%d,%d,%.6f,%.2f,%.4f,%.6f,%.4f,%.4f,%.4f,%.4f,%b,%b%n",
                            res.strategyFamily, res.paramSpec, res.policyId,
                            res.offeredTokens, res.admittedTokens, res.totalAdmittedRateNorm,
                            res.creditTokensDrawn, res.creditRateTokensPerSec, res.creditRateNorm,
                            res.maxAbuseScore, res.finalAbuseScore,
                            asympBound, res.exactTheorem2BoundRate,
                            res.exceedsAsymptotic, res.exceedsExactTheorem2Bound);
                }
            }
        }

        assertTrue(Files.exists(outCsv));

        // Theoretical verification on DREB-AC:
        // 1. The exact Theorem 2 bound MUST hold for EVERY simulation without exception!
        // 2. K(t) must never exceed the theoretical invariant maximum!
        for (SimulationResult res : results) {
            if (res.policyId.startsWith("dreb_ac")) {
                assertTrue(!res.exceedsExactTheorem2Bound,
                        String.format("Violation of Theorem 2 exact bound in %s [%s]: creditRate=%.4f > exactBound=%.4f",
                                res.policyId, res.paramSpec, res.creditRateTokensPerSec, res.exactTheorem2BoundRate));
                assertTrue(res.maxAbuseScore <= THEORETICAL_K_MAX,
                        String.format("Violation of invariant K_max in %s [%s]: maxK=%.4f > %.4f",
                                res.policyId, res.paramSpec, res.maxAbuseScore, THEORETICAL_K_MAX));
            }
        }
    }

    @Test
    @DisplayName("Verify Asymptotic Convergence of Credit Extraction Rate as T -> infinity")
    void verifyLongHorizonAsymptoticConvergence() {
        double[] horizons = {120.0, 600.0, 1200.0, 6000.0};
        double prevRate = Double.MAX_VALUE;
        double asympBound = IDLE_CAP * 0.05; // 42.8571

        for (double horizon : horizons) {
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            AdaptiveCeilingIdleCreditPolicy ac = new AdaptiveCeilingIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.75, 0.05);
            TrackingIdlePolicy tracking = new TrackingIdlePolicy(ac);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, tracking, S_MAX);

            // Attacker sends micro-pulses cost=500 every dt=0.01s (the most aggressive credit extractor)
            double t = 0.0;
            double dt = 0.01;
            while (t < horizon) {
                t += dt;
                clock.set(t);
                limiter.allow("adv", 500);
            }

            double creditRate = tracking.totalCreditSpent() / horizon;
            ClientState state = limiter.peekState("adv");
            double exactBound = (IDLE_CAP * (0.05 * horizon + state.abuseScore)) / horizon;

            assertTrue(creditRate <= exactBound + 1e-4, "Exact bound violated at horizon " + horizon);
            assertTrue(creditRate < prevRate, "Credit rate must decrease monotonically towards asymptotic limit with horizon T");
            prevRate = creditRate;

            if (horizon == 6000.0) {
                // At 6000s, rate should be within 1.5 tokens/s of the asymptotic limit (42.857)
                assertTrue(Math.abs(creditRate - asympBound) < 1.5,
                        String.format("Expected credit rate near %.4f, got %.4f", asympBound, creditRate));
            }
        }
    }
}
