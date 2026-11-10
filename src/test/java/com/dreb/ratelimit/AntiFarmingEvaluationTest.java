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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 4: Comprehensive Multi-Behavior Evaluation Suite for Anti-Farming Candidates.
 *
 * Tests the 6 client behaviors specified in Phase 1:
 * Behavior A: Legitimate client with occasional idle periods (Poisson traffic + pauses)
 * Behavior B: Legitimate client resuming after a single long idle period
 * Behavior C: Periodic attacker deliberately farming idle credit
 * Behavior D: Continuously active attacker (k = 1.5, 2.0, 4.0)
 * Behavior E: Client alternating between long idle periods and large bursts
 * Behavior F: Adaptive attacker that searches for the optimal waiting period
 *
 * Compares:
 * 1. DREB v1 (Burst-Matched Baseline)
 * 2. DREB v2 (Base 1.5 Baseline)
 * 3. Candidate 1: DREB-EMA (Exponential Memory Abuse, Half-Life = 25.0s)
 * 4. Candidate 2: DREB-AC (Adaptive Ceiling, Beta = 0.75)
 * 5. Candidate 3: DREB-CD (Credit Debt & Cooldown)
 * 6. Token Bucket (Capacity C, Rate R)
 * 7. Static Dual Bucket (B = 3C/7, S = 9C/7, zero idle credit)
 */
@DisplayName("Phase 1: Multi-Behavior Anti-Farming Comparative Evaluation")
public class AntiFarmingEvaluationTest {

    private static final double C = 1000.0;
    private static final double R = 1000.0;

    // Burst-matched parameters
    private static final double B_MAX = 3.0 * C / 7.0;       // 428.57
    private static final double B_RATE = 5.0 * R / 3.0;      // 428.57
    private static final double S_MAX = 9.0 * C / 7.0;       // 1285.71
    private static final double S_RATE = R;                  // 1000.0
    private static final double IDLE_CAP = 6.0 * C / 7.0;    // 857.14
    private static final double IDLE_GAIN = 2.0 * R / 3.0;   // 666.67
    private static final double TAU = IDLE_CAP / IDLE_GAIN;  // 1.2857 s
    private static final double T_REFILL = (C / R) + TAU;    // 2.2857 s

    public record PolicySpec(String id, String name) {}

    public interface LimiterFactory {
        RateLimiter create(Clock clock);
    }

    private static Map<String, LimiterFactory> getAlgorithms() {
        Map<String, LimiterFactory> algs = new LinkedHashMap<>();

        // 1. DREB v1 (Burst-Matched)
        algs.put("dreb_v1", clock -> Dreb.builder()
                .capacity(C).refillRate(R)
                .mapping(Dreb.Mapping.BURST_CEILING_MATCHED)
                .clock(clock).build());

        // 2. DREB v2 (Adaptive Base 1.5)
        algs.put("dreb_v2", clock -> Dreb.builderV2()
                .capacity(C).refillRate(R)
                .clock(clock).build());

        // 3. Candidate 1: DREB-EMA (Exponential Memory, T_half = 25.0s, abuseGain = 0.8)
        algs.put("dreb_ema", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            ExponentialAdaptiveIdleCreditPolicy policy = new ExponentialAdaptiveIdleCreditPolicy(
                    IDLE_GAIN, IDLE_CAP, 1.5, 0.8, 2.0, 25.0);
            return new DREBLimiter(store, clock, refill, policy, S_MAX);
        });

        // 4. Candidate 2: DREB-AC (Adaptive Ceiling, Beta = 0.75, recovery = 0.05/s)
        algs.put("dreb_ac", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            AdaptiveCeilingIdleCreditPolicy policy = new AdaptiveCeilingIdleCreditPolicy(
                    IDLE_GAIN, IDLE_CAP, 1.5, 0.75, 0.05);
            return new DREBLimiter(store, clock, refill, policy, S_MAX);
        });

        // 5. Candidate 3: DREB-CD (Credit Debt & Cooldown)
        algs.put("dreb_cd", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            CreditDebtIdleCreditPolicy policy = new CreditDebtIdleCreditPolicy(
                    IDLE_GAIN, IDLE_CAP, 1.5, 0.0);
            return new DREBLimiter(store, clock, refill, policy, S_MAX);
        });

        // 6. Token Bucket (Capacity C, Refill R)
        algs.put("token_bucket", clock -> new TokenBucketLimiter(new InMemoryStateStore(), clock, C, R));

        // 7. Static Dual Bucket (B = 3C/7, S = 9C/7, zero idle credit)
        algs.put("static_dual", clock -> {
            InMemoryStateStore store = new InMemoryStateStore();
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(B_MAX, B_RATE, S_MAX, S_RATE);
            LinearIdleCreditPolicy policy = new LinearIdleCreditPolicy(0.0, 0.0, 1.5);
            return new DREBLimiter(store, clock, refill, policy, S_MAX);
        });

        return algs;
    }

    @Test
    @DisplayName("Evaluate All 6 Client Behaviors Across Algorithms")
    void testEvaluateAllBehaviors() throws Exception {
        Path outDir = Paths.get("research/credit_farming");
        Files.createDirectories(outDir);
        Path csvFile = outDir.resolve("comparative_behaviors.csv");

        Map<String, LimiterFactory> algorithms = getAlgorithms();

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile.toFile()))) {
            pw.println("behavior,algorithm,offered_tokens,admitted_tokens,admitted_rate_norm,frr_percent,burst_acceptance_percent");

            // =========================================================================
            // BEHAVIOR A: Legitimate client with occasional idle periods
            // 60 seconds of Poisson traffic at 0.6R, followed by 10s idle, then 0.6R Poisson
            // Expected: FRR should be near 0% across all algorithms
            // =========================================================================
            for (var entry : algorithms.entrySet()) {
                String algId = entry.getKey();
                ManualClock clock = new ManualClock(0.0);
                RateLimiter limiter = entry.getValue().create(clock);
                ((AbstractRateLimiter) limiter).peekState("clientA");

                Random rng = new Random(42);
                long offered = 0;
                long admitted = 0;
                double t = 0.0;
                double step = 0.05; // 50ms steps

                while (t < 120.0) {
                    clock.set(t);
                    // Active periods: [0, 45s] and [75s, 120s]. Idle pause: [45s, 75s]
                    if (t < 45.0 || t >= 75.0) {
                        int count = samplePoisson(0.6 * R * step, rng);
                        for (int i = 0; i < count; i++) {
                            offered++;
                            if (limiter.allow("clientA", 1) == Decision.ALLOW) {
                                admitted++;
                            }
                        }
                    }
                    t += step;
                }

                double frr = offered > 0 ? (100.0 * (offered - admitted) / offered) : 0.0;
                double normRate = admitted / (R * 120.0);
                pw.printf("A_legit_occasional_idle,%s,%d,%d,%.4f,%.2f,%.2f%n",
                        algId, offered, admitted, normRate, frr, 100.0 - frr);
            }

            // =========================================================================
            // BEHAVIOR B: Legitimate client resuming after a single long idle period
            // Client rests for 60 seconds, then fires a legitimate burst of C (1000 tokens)
            // Expected: Burst acceptance should be 100% for DREB variants and TB
            // =========================================================================
            for (var entry : algorithms.entrySet()) {
                String algId = entry.getKey();
                ManualClock clock = new ManualClock(0.0);
                RateLimiter limiter = entry.getValue().create(clock);
                ((AbstractRateLimiter) limiter).peekState("clientB");

                clock.set(60.0); // 60s of genuine rest
                long offered = (long) C;
                long admitted = 0;
                for (int i = 0; i < (int) C; i++) {
                    if (limiter.allow("clientB", 1) == Decision.ALLOW) {
                        admitted++;
                    }
                }

                double burstAcc = (100.0 * admitted) / offered;
                pw.printf("B_legit_post_idle_burst,%s,%d,%d,%.4f,%.2f,%.2f%n",
                        algId, offered, admitted, admitted / C, 100.0 - burstAcc, burstAcc);
            }

            // =========================================================================
            // BEHAVIOR C: Periodic attacker deliberately farming idle credit
            // Waits D = T_REFILL = 2.286s, fires burst of C (1000 tokens), repeats for 120s
            // The core attack: we want low admitted traffic here for anti-farming candidates
            // =========================================================================
            for (var entry : algorithms.entrySet()) {
                String algId = entry.getKey();
                ManualClock clock = new ManualClock(0.0);
                RateLimiter limiter = entry.getValue().create(clock);
                ((AbstractRateLimiter) limiter).peekState("clientC");

                long offered = 0;
                long admitted = 0;
                double t = 0.0;
                while (t < 120.0) {
                    clock.set(t);
                    for (int i = 0; i < (int) C; i++) {
                        offered++;
                        if (limiter.allow("clientC", 1) == Decision.ALLOW) {
                            admitted++;
                        }
                    }
                    t += T_REFILL; // wait for credit refill
                }

                double normRate = admitted / (R * 120.0);
                double accPercent = (100.0 * admitted) / offered;
                pw.printf("C_periodic_credit_farmer,%s,%d,%d,%.4f,%.2f,%.2f%n",
                        algId, offered, admitted, normRate, 100.0 - accPercent, accPercent);
            }

            // =========================================================================
            // BEHAVIOR D: Continuously active attacker (k = 2.0R)
            // Heavy traffic at 2.0R continuously over 120 seconds
            // =========================================================================
            for (var entry : algorithms.entrySet()) {
                String algId = entry.getKey();
                ManualClock clock = new ManualClock(0.0);
                RateLimiter limiter = entry.getValue().create(clock);
                ((AbstractRateLimiter) limiter).peekState("clientD");

                long offered = 0;
                long admitted = 0;
                double t = 0.0;
                double step = 0.05; // 50ms
                Random rng = new Random(100);

                while (t < 120.0) {
                    clock.set(t);
                    int count = samplePoisson(2.0 * R * step, rng);
                    for (int i = 0; i < count; i++) {
                        offered++;
                        if (limiter.allow("clientD", 1) == Decision.ALLOW) {
                            admitted++;
                        }
                    }
                    t += step;
                }

                double normRate = admitted / (R * 120.0);
                pw.printf("D_continuous_attacker_2R,%s,%d,%d,%.4f,%.2f,%.2f%n",
                        algId, offered, admitted, normRate, 100.0 * (offered - admitted) / offered, (100.0 * admitted) / offered);
            }

            // =========================================================================
            // BEHAVIOR E: Alternator client (15s low load 0.3R, then 15s heavy burst train)
            // Evaluates transition between compliant resting and burst phases
            // =========================================================================
            for (var entry : algorithms.entrySet()) {
                String algId = entry.getKey();
                ManualClock clock = new ManualClock(0.0);
                RateLimiter limiter = entry.getValue().create(clock);
                ((AbstractRateLimiter) limiter).peekState("clientE");

                long offered = 0;
                long admitted = 0;
                double t = 0.0;
                double step = 0.05;
                Random rng = new Random(200);

                while (t < 120.0) {
                    clock.set(t);
                    boolean isIdlePhase = ((int) (t / 15.0)) % 2 == 0;
                    double offeredRate = isIdlePhase ? 0.3 * R : 2.0 * R;

                    int count = samplePoisson(offeredRate * step, rng);
                    for (int i = 0; i < count; i++) {
                        offered++;
                        if (limiter.allow("clientE", 1) == Decision.ALLOW) {
                            admitted++;
                        }
                    }
                    t += step;
                }

                double normRate = admitted / (R * 120.0);
                pw.printf("E_alternating_idle_burst,%s,%d,%d,%.4f,%.2f,%.2f%n",
                        algId, offered, admitted, normRate, 100.0 * (offered - admitted) / offered, (100.0 * admitted) / offered);
            }

            // =========================================================================
            // BEHAVIOR F: Adaptive attacker dynamically varying waiting period
            // Cycles through intervals D in {0.5tau, 1.0tau, 1.5tau, 2.0tau, 3.0tau}
            // =========================================================================
            for (var entry : algorithms.entrySet()) {
                String algId = entry.getKey();
                ManualClock clock = new ManualClock(0.0);
                RateLimiter limiter = entry.getValue().create(clock);
                ((AbstractRateLimiter) limiter).peekState("clientF");

                long offered = 0;
                long admitted = 0;
                double t = 0.0;
                double[] delays = {0.5 * TAU, 1.0 * TAU, 1.5 * TAU, 2.0 * TAU, 3.0 * TAU};
                int dIdx = 0;

                while (t < 120.0) {
                    clock.set(t);
                    for (int i = 0; i < (int) C; i++) {
                        offered++;
                        if (limiter.allow("clientF", 1) == Decision.ALLOW) {
                            admitted++;
                        }
                    }
                    t += delays[dIdx % delays.length];
                    dIdx++;
                }

                double normRate = admitted / (R * 120.0);
                pw.printf("F_adaptive_varying_delay_attacker,%s,%d,%d,%.4f,%.2f,%.2f%n",
                        algId, offered, admitted, normRate, 100.0 * (offered - admitted) / offered, (100.0 * admitted) / offered);
            }
        }

        assertTrue(Files.exists(csvFile));
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
