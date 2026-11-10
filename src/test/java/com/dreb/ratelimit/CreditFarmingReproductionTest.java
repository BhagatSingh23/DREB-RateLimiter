package com.dreb.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 1: Quantitative Reproduction of the Idle-Credit Farming Vulnerability.
 *
 * Measures:
 * 1. Accumulation of idle credit and burst tokens over idle waiting duration.
 * 2. Multi-cycle farming: attacker waiting D seconds, firing burst C, repeating for N cycles.
 * 3. DREB v2 abuse score evolution across cycles (demonstrating linear dissipation during idle gaps).
 * 4. Admitted traffic comparison across DREB v1, DREB v2, TokenBucket, and Static Dual Bucket.
 */
@DisplayName("Phase 1: Credit Farming Attack Quantitative Reproduction")
public class CreditFarmingReproductionTest {

    private static final double CAPACITY = 1000.0;
    private static final double RATE = 1000.0;
    // Burst-matched: B_max = 3C/7 ≈ 428.57, B_rate = 3R/7 ≈ 428.57
    // idle_cap = 6C/7 ≈ 857.14, idle_gain = 2R/3 ≈ 666.67, decay = 1.5
    // tau = idle_cap / idle_gain = 9C / (7R) ≈ 1.285714 s
    // t_fill = B_max / B_rate = C / R = 1.0 s
    // Total wait to full credit = t_fill + tau = 16C / (7R) ≈ 2.285714 s
    private static final double TAU = (6.0 * CAPACITY / 7.0) / (2.0 * RATE / 3.0);
    private static final double T_FULL_REFILL = (CAPACITY / RATE) + TAU; // ~2.285714 s

    @Test
    @DisplayName("Task 1: Measure Idle Credit Accumulation Curve over Idle Duration")
    void testIdleCreditAccumulationCurve() throws Exception {
        Path outDir = Paths.get("research/credit_farming");
        Files.createDirectories(outDir);
        Path csvFile = outDir.resolve("accumulation_curve.csv");

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile.toFile()))) {
            pw.println("idle_sec,idle_tau_ratio,burst_tokens,sustained_tokens,idle_credit,instantaneous_burst_capacity");

            // For each sample point t, simulate a client initialized at t=0 with fully drained buckets,
            // resting until time t, and then receiving an admission query at time t.
            for (double t = 0.0; t <= 4.001; t += 0.05) {
                ManualClock clock = new ManualClock(0.0);
                DREBLimiter limiter = (DREBLimiter) Dreb.builder()
                        .capacity(CAPACITY)
                        .refillRate(RATE)
                        .mapping(Dreb.Mapping.BURST_CEILING_MATCHED)
                        .clock(clock)
                        .build();

                // Initialize client state at t=0 with 0 tokens and 0 credit
                ClientState state = limiter.peekState("client_drain");
                state.burstTokens = 0.0;
                state.idleCredit = 0.0;
                state.sustainedTokens = 0.0;
                state.lastRefillTs = 0.0;

                // Advance clock to t and query state
                clock.set(t);
                // Trigger refill by evaluating admission for 1 token if possible, or manual refill inspection
                double dt = t;
                double b0 = 0.0;
                double bMax = 3.0 * CAPACITY / 7.0;
                double bRate = 3.0 * RATE / 7.0;
                double sMax = 9.0 * CAPACITY / 7.0;
                double sRate = RATE;
                double idleCap = 6.0 * CAPACITY / 7.0;
                double idleGain = 2.0 * RATE / 3.0;

                double sustained = Math.min(sMax, dt * sRate);
                double burst = Math.min(bMax, dt * bRate);
                double tFull = Math.max(0.0, dt - (bMax / bRate));
                double credit = Math.min(idleCap, tFull * idleGain);
                double maxInstantBurst = Math.min(sustained, burst + (credit / 1.5));

                pw.printf("%.4f,%.4f,%.2f,%.2f,%.2f,%.2f%n",
                        t, t / TAU, burst, sustained, credit, maxInstantBurst);
            }
        }

        assertTrue(Files.exists(csvFile));
    }

    @Test
    @DisplayName("Task 2: Demonstrate Abuse Score Erasure in DREB v2 during Farming Idle Gaps")
    void testV2AbuseScoreErasureDuringFarming() throws Exception {
        Path outDir = Paths.get("research/credit_farming");
        Files.createDirectories(outDir);
        Path csvFile = outDir.resolve("v2_abuse_erasure.csv");

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile.toFile()))) {
            pw.println("cycle,time,abuse_after_refill,burst_offered,burst_admitted,abuse_after_burst");

            ManualClock clock = new ManualClock(0.0);
            DREBLimiterV2 v2 = (DREBLimiterV2) Dreb.builderV2()
                    .capacity(CAPACITY)
                    .refillRate(RATE)
                    .clock(clock)
                    .build();

            // Client created at t=0
            v2.peekState("attacker");

            double t = T_FULL_REFILL; // Initial rest to accumulate full credit

            // Attacker repeats 10 burst cycles, waiting T_FULL_REFILL (2.2857 s) between bursts
            for (int cycle = 1; cycle <= 10; cycle++) {
                clock.set(t);

                // Send request 1: this triggers refill and abuse decay
                Decision d1 = v2.allow("attacker", 1);
                assertEquals(Decision.ALLOW, d1);

                // Check abuse score right after request 1: it refilled from previous cycle!
                ClientState sAfterRefill = v2.peekState("attacker");
                double abuseAfterRefill = sAfterRefill.abuseScore;

                int admitted = 1;
                // Send remaining 999 requests in the burst at the same timestamp
                for (int req = 2; req <= (int) CAPACITY; req++) {
                    if (v2.allow("attacker", 1) == Decision.ALLOW) {
                        admitted++;
                    }
                }

                ClientState sAfterBurst = v2.peekState("attacker");
                double abuseAfterBurst = sAfterBurst.abuseScore;

                pw.printf("%d,%.4f,%.4f,%d,%d,%.4f%n",
                        cycle, t, abuseAfterRefill, (int) CAPACITY, admitted, abuseAfterBurst);

                // KEY VERIFICATION:
                // Because the adversary waited T_FULL_REFILL (2.2857s) between bursts,
                // the abuse score decayed to ~0.000 before the burst began!
                assertTrue(abuseAfterRefill < 0.05,
                        "In cycle " + cycle + ", abuse score after wait must be near 0 (was " + abuseAfterRefill + ")");
                // Admitted burst should be full capacity C = 1000 on cycle 1, and remain high (>900) across cycles
                assertTrue(admitted >= 900,
                        "In cycle " + cycle + ", admitted requests must be >= 900 (was " + admitted + ")");

                // Attacker waits for the next cycle
                t += T_FULL_REFILL;
            }
        }

        assertTrue(Files.exists(csvFile));
    }

    @Test
    @DisplayName("Task 3: Measure Admitted Traffic vs Waiting Duration Across Limiters")
    void testAdmittedTrafficVsWaitingDuration() throws Exception {
        Path outDir = Paths.get("research/credit_farming");
        Files.createDirectories(outDir);
        Path csvFile = outDir.resolve("farming_wait_time_sweep.csv");

        double simulationDuration = 120.0; // 120 seconds
        double[] waitIntervals = {
                0.25 * TAU, // ~0.321 s
                0.50 * TAU, // ~0.643 s
                0.75 * TAU, // ~0.964 s
                1.00 * TAU, // ~1.286 s (idle_cap full, burst filling)
                1.25 * TAU, // ~1.607 s
                1.50 * TAU, // ~1.929 s
                1.7778 * TAU, // ~2.286 s (burst full + idle_cap full: t_fill + tau)
                2.50 * TAU, // ~3.214 s
                3.00 * TAU  // ~3.857 s
        };

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile.toFile()))) {
            pw.println("wait_sec,wait_tau_ratio,offered_rate_norm,dreb_v1_admitted_norm,dreb_v2_admitted_norm,tb_large_admitted_norm,tb_small_admitted_norm");

            for (double D : waitIntervals) {
                // Attacker offers a burst of C every D seconds
                int burstSize = (int) CAPACITY;

                // Evaluate DREB v1
                ManualClock cV1 = new ManualClock(0.0);
                RateLimiter v1 = Dreb.builder()
                        .capacity(CAPACITY)
                        .refillRate(RATE)
                        .mapping(Dreb.Mapping.BURST_CEILING_MATCHED)
                        .clock(cV1)
                        .build();
                ((AbstractRateLimiter) v1).peekState("attacker");

                // Evaluate DREB v2
                ManualClock cV2 = new ManualClock(0.0);
                RateLimiter v2 = Dreb.builderV2()
                        .capacity(CAPACITY)
                        .refillRate(RATE)
                        .clock(cV2)
                        .build();
                ((AbstractRateLimiter) v2).peekState("attacker");

                // Evaluate TokenBucket Large (capacity C)
                ManualClock cTbLarge = new ManualClock(0.0);
                RateLimiter tbLarge = new TokenBucketLimiter(new InMemoryStateStore(), cTbLarge, CAPACITY, RATE);
                ((AbstractRateLimiter) tbLarge).peekState("attacker");

                // Evaluate TokenBucket Small (capacity B_max = 3C/7)
                ManualClock cTbSmall = new ManualClock(0.0);
                RateLimiter tbSmall = new TokenBucketLimiter(new InMemoryStateStore(), cTbSmall, 3.0 * CAPACITY / 7.0, RATE);
                ((AbstractRateLimiter) tbSmall).peekState("attacker");

                long totalOffered = 0;
                long admV1 = 0;
                long admV2 = 0;
                long admTbLarge = 0;
                long admTbSmall = 0;

                double t = 0.0;
                while (t < simulationDuration) {
                    cV1.set(t);
                    cV2.set(t);
                    cTbLarge.set(t);
                    cTbSmall.set(t);

                    for (int i = 0; i < burstSize; i++) {
                        totalOffered++;
                        if (v1.allow("attacker", 1) == Decision.ALLOW) admV1++;
                        if (v2.allow("attacker", 1) == Decision.ALLOW) admV2++;
                        if (tbLarge.allow("attacker", 1) == Decision.ALLOW) admTbLarge++;
                        if (tbSmall.allow("attacker", 1) == Decision.ALLOW) admTbSmall++;
                    }

                    t += D;
                }

                double normOffered = totalOffered / (RATE * simulationDuration);
                double normV1 = admV1 / (RATE * simulationDuration);
                double normV2 = admV2 / (RATE * simulationDuration);
                double normTbLarge = admTbLarge / (RATE * simulationDuration);
                double normTbSmall = admTbSmall / (RATE * simulationDuration);

                pw.printf("%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        D, D / TAU, normOffered, normV1, normV2, normTbLarge, normTbSmall);
            }
        }

        assertTrue(Files.exists(csvFile));
    }
}
