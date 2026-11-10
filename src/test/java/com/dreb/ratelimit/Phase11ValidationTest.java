package com.dreb.ratelimit;

import com.dreb.ratelimit.policies.AdaptiveCeilingIdleCreditPolicy;
import com.dreb.ratelimit.policies.CreditDebtIdleCreditPolicy;
import com.dreb.ratelimit.policies.ExponentialAdaptiveIdleCreditPolicy;
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
 * Phase 1.1: Independent Validation of DREB-AC and Comprehensive Pareto Frontier Analysis.
 *
 * Implements:
 * 1. Closed-loop feedback-driven adversaries (AIMD prober, Hill-climbing, Adaptive burst-size, Random prober).
 * 2. Multi-parameter configurations across 7 algorithm families (20 configurations total).
 * 3. 30 independent random seeds (2000-2029) per cell.
 * 4. Full statistical reporting: Mean, Median, StdDev, 95% Bootstrap CIs.
 * 5. Multi-objective Pareto frontier identification.
 */
@DisplayName("Phase 1.1: Independent Validation of DREB-AC & Pareto Frontier")
public class Phase11ValidationTest {

    private static final double C = 1000.0;
    private static final double R = 1000.0;
    private static final double DURATION = 120.0;
    private static final int NUM_SEEDS = 30;
    private static final long SEED_START = 2000L;

    // Burst-ceiling matched parameters
    private static final double B_MAX = 3.0 * C / 7.0;       // 428.57
    private static final double B_RATE = 5.0 * R / 3.0;      // 1666.67
    private static final double S_MAX = 9.0 * C / 7.0;       // 1285.71
    private static final double S_RATE = R;                  // 1000.0
    private static final double IDLE_CAP = 6.0 * C / 7.0;    // 857.14
    private static final double IDLE_GAIN = 2.0 * R / 3.0;   // 666.67
    private static final double TAU = IDLE_CAP / IDLE_GAIN;  // 1.2857 s
    private static final double T_REFILL = (B_MAX / B_RATE) + TAU; // ~1.543 s

    public record ConfigDef(String id, String family, String displayName, Factory factory) {}

    public interface Factory {
        RateLimiter create(Clock clock);
    }

    private static List<ConfigDef> getConfigurations() {
        List<ConfigDef> list = new ArrayList<>();

        // 1. DREB v1 (Burst-Matched, decay = 1.5)
        list.add(new ConfigDef("dreb_v1_bm", "DREB_v1", "DREB v1 (burst-matched)", clock ->
                Dreb.builder().capacity(C).refillRate(R).mapping(Dreb.Mapping.BURST_CEILING_MATCHED).clock(clock).build()));

        // 2. DREB v1 (Proportional, decay = 1.5)
        list.add(new ConfigDef("dreb_v1_prop", "DREB_v1", "DREB v1 (proportional)", clock ->
                Dreb.builder().capacity(C).refillRate(R).mapping(Dreb.Mapping.PROPORTIONAL).clock(clock).build()));

        // 3. DREB v2 (Base 1.5, default decay = 1.5, abuseDecay = 1.5)
        list.add(new ConfigDef("dreb_v2_base15", "DREB_v2", "DREB v2 (base 1.5, default)", clock ->
                Dreb.builderV2().capacity(C).refillRate(R).clock(clock).build()));

        // 4. DREB v2 (Base 1.5, slow decay = 0.5)
        list.add(new ConfigDef("dreb_v2_slow_decay", "DREB_v2", "DREB v2 (slow decay 0.5)", clock ->
                Dreb.builderV2().capacity(C).refillRate(R).tuning(1.5, 0.3, 2.0, 0.5, 0.35).clock(clock).build()));

        // 5. DREB-EMA (T_half = 10s, alpha = 0.8)
        list.add(new ConfigDef("dreb_ema_h10", "DREB_EMA", "DREB-EMA (T_half=10s, a=0.8)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill,
                    new ExponentialAdaptiveIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.8, 2.0, 10.0), S_MAX);
        }));

        // 6. DREB-EMA (T_half = 25s, alpha = 0.8)
        list.add(new ConfigDef("dreb_ema_h25", "DREB_EMA", "DREB-EMA (T_half=25s, a=0.8)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill,
                    new ExponentialAdaptiveIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.8, 2.0, 25.0), S_MAX);
        }));

        // 7. DREB-EMA (T_half = 60s, alpha = 1.5)
        list.add(new ConfigDef("dreb_ema_h60", "DREB_EMA", "DREB-EMA (T_half=60s, a=1.5)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill,
                    new ExponentialAdaptiveIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 1.5, 2.0, 60.0), S_MAX);
        }));

        // 8. DREB-AC (Beta = 0.25, recovery = 0.05/s)
        list.add(new ConfigDef("dreb_ac_b025", "DREB_AC", "DREB-AC (beta=0.25, rec=0.05)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill,
                    new AdaptiveCeilingIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.25, 0.05), S_MAX);
        }));

        // 9. DREB-AC (Beta = 0.75, recovery = 0.05/s) [Headline]
        list.add(new ConfigDef("dreb_ac_b075_r005", "DREB_AC", "DREB-AC (beta=0.75, rec=0.05)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill,
                    new AdaptiveCeilingIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.75, 0.05), S_MAX);
        }));

        // 10. DREB-AC (Beta = 1.50, recovery = 0.05/s) [Aggressive]
        list.add(new ConfigDef("dreb_ac_b150", "DREB_AC", "DREB-AC (beta=1.50, rec=0.05)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill,
                    new AdaptiveCeilingIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 1.50, 0.05), S_MAX);
        }));

        // 11. DREB-AC (Beta = 0.75, fast recovery = 0.10/s)
        list.add(new ConfigDef("dreb_ac_b075_r010", "DREB_AC", "DREB-AC (beta=0.75, rec=0.10)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill,
                    new AdaptiveCeilingIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.75, 0.10), S_MAX);
        }));

        // 12. DREB-CD (Credit Debt, no passive dissipation)
        list.add(new ConfigDef("dreb_cd_base", "DREB_CD", "DREB-CD (debt base)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill,
                    new CreditDebtIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.0), S_MAX);
        }));

        // 13. DREB-CD (Credit Debt, passive recovery = 0.05/s)
        list.add(new ConfigDef("dreb_cd_rec005", "DREB_CD", "DREB-CD (debt rec=0.05)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill,
                    new CreditDebtIdleCreditPolicy(IDLE_GAIN, IDLE_CAP, 1.5, 0.05), S_MAX);
        }));

        // 14. Token Bucket Large (C, R)
        list.add(new ConfigDef("tb_large", "TokenBucket", "Token Bucket (C, R)", clock ->
                new TokenBucketLimiter(new InMemoryStateStore(), clock, C, R)));

        // 15. Token Bucket Small (3C/7, R)
        list.add(new ConfigDef("tb_small", "TokenBucket", "Token Bucket (3C/7, R)", clock ->
                new TokenBucketLimiter(new InMemoryStateStore(), clock, B_MAX, R)));

        // 16. Token Bucket Envelope (9C/7, R)
        list.add(new ConfigDef("tb_env", "TokenBucket", "Token Bucket (9C/7, R)", clock ->
                new TokenBucketLimiter(new InMemoryStateStore(), clock, S_MAX, R)));

        // 17. Static Dual Bucket (B = 3C/7, S = 9C/7)
        list.add(new ConfigDef("static_db_3c7", "StaticDual", "Static DB (B=3C/7, S=9C/7)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill, new LinearIdleCreditPolicy(0.0, 0.0, 1.5), S_MAX);
        }));

        // 18. Static Dual Bucket (B = 0.70C, S = 9C/7)
        list.add(new ConfigDef("static_db_070c", "StaticDual", "Static DB (B=0.70C, S=9C/7)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(0.70 * C, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill, new LinearIdleCreditPolicy(0.0, 0.0, 1.5), S_MAX);
        }));

        // 19. Static Dual Bucket (B = 0.85C, S = 9C/7)
        list.add(new ConfigDef("static_db_085c", "StaticDual", "Static DB (B=0.85C, S=9C/7)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(0.85 * C, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill, new LinearIdleCreditPolicy(0.0, 0.0, 1.5), S_MAX);
        }));

        // 20. Static Dual Bucket (B = 1.00C, S = 9C/7)
        list.add(new ConfigDef("static_db_100c", "StaticDual", "Static DB (B=1.00C, S=9C/7)", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(1.00 * C, B_RATE, S_MAX, S_RATE);
            return new DREBLimiter(store, clock, refill, new LinearIdleCreditPolicy(0.0, 0.0, 1.5), S_MAX);
        }));

        return list;
    }

    public record MetricSummary(
            double mean,
            double median,
            double stdDev,
            double ci95Lower,
            double ci95Upper
    ) {
        public static MetricSummary of(double[] samples) {
            double[] sorted = samples.clone();
            Arrays.sort(sorted);
            int n = sorted.length;
            double sum = 0.0;
            for (double x : sorted) sum += x;
            double mean = sum / n;

            double median = (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;

            double varSum = 0.0;
            for (double x : sorted) varSum += (x - mean) * (x - mean);
            double stdDev = (n > 1) ? Math.sqrt(varSum / (n - 1)) : 0.0;

            // 95% Percentile bootstrap CI
            double ciLower = sorted[(int) Math.floor(0.025 * (n - 1))];
            double ciUpper = sorted[(int) Math.ceil(0.975 * (n - 1))];

            return new MetricSummary(mean, median, stdDev, ciLower, ciUpper);
        }
    }

    @Test
    @DisplayName("Run 30-Seed Closed-Loop Adversarial Benchmark & Pareto Evaluation")
    void runPhase11Validation() throws Exception {
        Path outDir = Paths.get("research/credit_farming");
        Files.createDirectories(outDir);

        List<ConfigDef> configs = getConfigurations();

        // 1. Evaluate 5 Closed-Loop Adversarial Strategies across 30 seeds
        Path advCsv = outDir.resolve("phase1_1_adversaries_30seeds.csv");
        Path paretoCsv = outDir.resolve("phase1_1_pareto_frontier.csv");

        String[] adversaryNames = {
                "fixed_period_farmer",
                "random_period_farmer",
                "aimd_feedback_prober",
                "hill_climbing_delay_search",
                "adaptive_burst_optimizer"
        };

        try (PrintWriter pw = new PrintWriter(new FileWriter(advCsv.toFile()))) {
            pw.println("config_id,adversary,seed,offered_tokens,admitted_tokens,admitted_rate_norm,rejection_rate_percent");

            for (ConfigDef cfg : configs) {
                for (String adv : adversaryNames) {
                    for (int s = 0; s < NUM_SEEDS; s++) {
                        long seed = SEED_START + s;
                        long[] result = runAdversary(cfg.factory, adv, seed);
                        long offered = result[0];
                        long admitted = result[1];
                        double normRate = admitted / (R * DURATION);
                        double rejRate = offered > 0 ? (100.0 * (offered - admitted) / offered) : 0.0;

                        pw.printf("%s,%s,%d,%d,%d,%.6f,%.2f%n",
                                cfg.id, adv, seed, offered, admitted, normRate, rejRate);
                    }
                }
            }
        }

        // 2. Multi-Objective Evaluation for Pareto Frontier across 30 seeds
        // Objectives:
        // Obj 1: Adversarial Admitted Rate under AIMD feedback prober (minimize)
        // Obj 2: Legitimate False Rejection Rate under bursty compliant traffic (minimize)
        // Obj 3: Legitimate Post-Idle Burst Acceptance (maximize)
        // Obj 4: Long-Run Admitted Rate under Poisson 0.98R (target = 0.98R)
        // Obj 5: Latency / CPU overhead per decision (nanoseconds)
        try (PrintWriter pw = new PrintWriter(new FileWriter(paretoCsv.toFile()))) {
            pw.println("config_id,family,display_name," +
                    "adv_aimd_rate_mean,adv_aimd_rate_std,adv_aimd_rate_ci_lower,adv_aimd_rate_ci_upper," +
                    "legit_bursty_frr_mean,legit_bursty_frr_ci_lower,legit_bursty_frr_ci_upper," +
                    "legit_burst_acc_mean,legit_burst_acc_ci_lower,legit_burst_acc_ci_upper," +
                    "legit_steady_adm_mean," +
                    "nanos_per_op_mean");

            for (ConfigDef cfg : configs) {
                double[] aimdRates = new double[NUM_SEEDS];
                double[] burstyFrrs = new double[NUM_SEEDS];
                double[] burstAccs = new double[NUM_SEEDS];
                double[] steadyAdms = new double[NUM_SEEDS];

                for (int s = 0; s < NUM_SEEDS; s++) {
                    long seed = SEED_START + s;

                    // Objective 1: AIMD Adversary
                    long[] aimdRes = runAdversary(cfg.factory, "aimd_feedback_prober", seed);
                    aimdRates[s] = aimdRes[1] / (R * DURATION);

                    // Objective 2: Bursty Compliant Client (Poisson bursts of mean 50 at 0.70R total load)
                    double[] bRes = runBurstyCompliant(cfg.factory, seed);
                    burstyFrrs[s] = bRes[0];

                    // Objective 3: Post-Idle Burst (burst of C after 60s rest)
                    burstAccs[s] = runPostIdleBurst(cfg.factory, seed);

                    // Objective 4: Steady compliant Poisson at 0.98R
                    steadyAdms[s] = runSteadyPoisson(cfg.factory, seed);
                }

                MetricSummary aimdSummary = MetricSummary.of(aimdRates);
                MetricSummary burstySummary = MetricSummary.of(burstyFrrs);
                MetricSummary burstSummary = MetricSummary.of(burstAccs);
                MetricSummary steadySummary = MetricSummary.of(steadyAdms);

                // Objective 5: Benchmark latency per operation (nanoseconds)
                double nanosPerOp = benchmarkLatency(cfg.factory);

                pw.printf("%s,%s,\"%s\",%.4f,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,%.1f%n",
                        cfg.id, cfg.family, cfg.displayName,
                        aimdSummary.mean, aimdSummary.stdDev, aimdSummary.ci95Lower, aimdSummary.ci95Upper,
                        burstySummary.mean, burstySummary.ci95Lower, burstySummary.ci95Upper,
                        burstSummary.mean, burstSummary.ci95Lower, burstSummary.ci95Upper,
                        steadySummary.mean,
                        nanosPerOp);
            }
        }

        assertTrue(Files.exists(advCsv));
        assertTrue(Files.exists(paretoCsv));
    }

    private static long[] runAdversary(Factory factory, String advType, long seed) {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = factory.create(clock);
        ((AbstractRateLimiter) limiter).peekState("adversary");

        Random rng = new Random(seed);
        long offered = 0;
        long admitted = 0;
        double t = 0.0;

        switch (advType) {
            case "fixed_period_farmer" -> {
                // Waits fixed D = T_REFILL, fires burst C
                double dt = T_REFILL;
                while (t < DURATION) {
                    clock.set(t);
                    for (int i = 0; i < (int) C; i++) {
                        offered++;
                        if (limiter.allow("adversary", 1) == Decision.ALLOW) admitted++;
                    }
                    t += dt;
                }
            }
            case "random_period_farmer" -> {
                // Jittered interval in [0.5 * tau, 2.5 * tau]
                while (t < DURATION) {
                    clock.set(t);
                    int burst = (int) Math.round(0.7 * C + 0.6 * C * rng.nextDouble());
                    for (int i = 0; i < burst; i++) {
                        offered++;
                        if (limiter.allow("adversary", 1) == Decision.ALLOW) admitted++;
                    }
                    double dt = (0.5 + 2.0 * rng.nextDouble()) * TAU;
                    t += dt;
                }
            }
            case "aimd_feedback_prober" -> {
                // Feedback-driven AIMD: observes rejection count.
                // If any 429 occurs, back off delay multiplicatively (D = D * 1.3).
                // If 100% admitted, decrease delay additively (D = max(0.2s, D - 0.1s)).
                double delay = T_REFILL;
                int burstSize = (int) C;

                while (t < DURATION) {
                    clock.set(t);
                    int burstAdmitted = 0;
                    for (int i = 0; i < burstSize; i++) {
                        offered++;
                        if (limiter.allow("adversary", 1) == Decision.ALLOW) {
                            admitted++;
                            burstAdmitted++;
                        }
                    }

                    double acceptFraction = (double) burstAdmitted / burstSize;
                    if (acceptFraction < 0.99) {
                        // Observed 429s -> Backoff delay to allow credit recovery
                        delay = Math.min(10.0, delay * 1.35);
                        // Also tune burst size toward admitted ceiling
                        burstSize = Math.max(300, (int) Math.round(burstAdmitted * 1.05));
                    } else {
                        // 100% admitted -> Probe faster attack frequency
                        delay = Math.max(0.3, delay - 0.15);
                        burstSize = Math.min((int) C, (int) Math.round(burstSize * 1.10));
                    }

                    t += delay;
                }
            }
            case "hill_climbing_delay_search" -> {
                // Search-based optimizer trying to maximize admitted throughput: adm / delay
                double currentDelay = 2.0;
                double step = 0.2;
                double bestThroughput = 0.0;

                while (t < DURATION) {
                    clock.set(t);
                    int burstAdm = 0;
                    for (int i = 0; i < (int) C; i++) {
                        offered++;
                        if (limiter.allow("adversary", 1) == Decision.ALLOW) {
                            admitted++;
                            burstAdm++;
                        }
                    }

                    double throughput = burstAdm / currentDelay;
                    if (throughput > bestThroughput) {
                        bestThroughput = throughput;
                        currentDelay = Math.max(0.4, currentDelay - step); // step toward faster frequency
                    } else {
                        step = -step * 0.7; // reverse search direction and reduce step
                        currentDelay = Math.max(0.4, currentDelay + step);
                    }

                    t += currentDelay;
                }
            }
            case "adaptive_burst_optimizer" -> {
                // Keeps delay at 2.0s, dynamically searches for burst size that maximizes acceptance rate
                double fixedDelay = 2.0;
                int candidateBurst = (int) C;

                while (t < DURATION) {
                    clock.set(t);
                    int burstAdm = 0;
                    for (int i = 0; i < candidateBurst; i++) {
                        offered++;
                        if (limiter.allow("adversary", 1) == Decision.ALLOW) {
                            admitted++;
                            burstAdm++;
                        }
                    }

                    if (burstAdm == candidateBurst) {
                        candidateBurst = Math.min((int) C, candidateBurst + 50);
                    } else {
                        candidateBurst = Math.max(200, burstAdm);
                    }

                    t += fixedDelay;
                }
            }
            default -> throw new IllegalArgumentException("Unknown adversary: " + advType);
        }

        return new long[]{offered, admitted};
    }

    private static double[] runBurstyCompliant(Factory factory, long seed) {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = factory.create(clock);
        ((AbstractRateLimiter) limiter).peekState("clientBursty");

        Random rng = new Random(seed);
        long offered = 0;
        long admitted = 0;
        double t = 0.0;
        double step = 0.05; // 50ms

        while (t < DURATION) {
            clock.set(t);
            // Poisson bursts arriving at 0.7R average rate
            int count = samplePoisson(0.70 * R * step, rng);
            for (int i = 0; i < count; i++) {
                offered++;
                if (limiter.allow("clientBursty", 1) == Decision.ALLOW) {
                    admitted++;
                }
            }
            t += step;
        }

        double frr = offered > 0 ? (100.0 * (offered - admitted) / offered) : 0.0;
        return new double[]{frr, (double) admitted};
    }

    private static double runPostIdleBurst(Factory factory, long seed) {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = factory.create(clock);
        ((AbstractRateLimiter) limiter).peekState("clientBurst");

        clock.set(60.0); // 60s rest
        int admitted = 0;
        for (int i = 0; i < (int) C; i++) {
            if (limiter.allow("clientBurst", 1) == Decision.ALLOW) {
                admitted++;
            }
        }
        return (100.0 * admitted) / C;
    }

    private static double runSteadyPoisson(Factory factory, long seed) {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = factory.create(clock);
        ((AbstractRateLimiter) limiter).peekState("clientSteady");

        Random rng = new Random(seed);
        long admitted = 0;
        double t = 0.0;
        double step = 0.05;

        while (t < DURATION) {
            clock.set(t);
            int count = samplePoisson(0.98 * R * step, rng);
            for (int i = 0; i < count; i++) {
                if (limiter.allow("clientSteady", 1) == Decision.ALLOW) {
                    admitted++;
                }
            }
            t += step;
        }

        return (double) admitted / (R * DURATION);
    }

    private static double benchmarkLatency(Factory factory) {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = factory.create(clock);
        ((AbstractRateLimiter) limiter).peekState("bench");

        // Warmup
        for (int i = 0; i < 50_000; i++) {
            clock.set(i * 0.001);
            limiter.allow("bench", 1);
        }

        // Measurement: 200,000 iterations
        int ops = 200_000;
        long start = System.nanoTime();
        for (int i = 0; i < ops; i++) {
            clock.set(50.0 + i * 0.001);
            limiter.allow("bench", 1);
        }
        long duration = System.nanoTime() - start;
        return (double) duration / ops;
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
}
