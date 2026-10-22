package com.dreb.ratelimit;

import net.jqwik.api.*;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DrebPropertiesTest {

    record RequestStep(double dt, int cost) {}

    @Provide
    Arbitrary<List<RequestStep>> requestTrace() {
        Arbitrary<Double> dtArb = Arbitraries.doubles().between(0.0, 5.0);
        Arbitrary<Integer> costArb = Arbitraries.integers().between(1, 20);
        Arbitrary<RequestStep> stepArb = Combinators.combine(dtArb, costArb).as(RequestStep::new);
        return stepArb.list().ofMinSize(5).ofMaxSize(100);
    }

    @Property
    void stateInvariantsHoldForV1(
            @ForAll("requestTrace") List<RequestStep> trace,
            @ForAll @DoubleRange(min = 5.0, max = 50.0) double capacity,
            @ForAll @DoubleRange(min = 1.0, max = 20.0) double refillRate
    ) {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = Dreb.builder()
                .capacity(capacity)
                .refillRate(refillRate)
                .clock(clock)
                .build();

        AbstractRateLimiter dreb = (AbstractRateLimiter) limiter;
        ClientState initial = dreb.peekState("u");
        double burstMax = initial.burstTokens;
        double sustainedMax = initial.sustainedTokens;
        double idleCap = burstMax * 2.0;

        double currentTime = 0.0;
        for (RequestStep step : trace) {
            currentTime += step.dt();
            clock.set(currentTime);
            dreb.allow("u", step.cost());

            ClientState s = dreb.peekState("u");
            assertTrue(s.burstTokens >= 0.0, "burstTokens must be >= 0");
            assertTrue(s.burstTokens <= burstMax + 1e-6, "burstTokens must be <= burstMax");
            assertTrue(s.sustainedTokens >= 0.0, "sustainedTokens must be >= 0");
            assertTrue(s.sustainedTokens <= sustainedMax + 1e-6, "sustainedTokens must be <= sustainedMax");
            assertTrue(s.idleCredit >= 0.0, "idleCredit must be >= 0");
            assertTrue(s.idleCredit <= idleCap + 1e-6, "idleCredit must be <= idleCap");
        }
    }

    @Property
    void stateInvariantsHoldForV2(
            @ForAll("requestTrace") List<RequestStep> trace,
            @ForAll @DoubleRange(min = 5.0, max = 50.0) double capacity,
            @ForAll @DoubleRange(min = 1.0, max = 20.0) double refillRate
    ) {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = Dreb.builderV2()
                .capacity(capacity)
                .refillRate(refillRate)
                .clock(clock)
                .build();

        AbstractRateLimiter dreb = (AbstractRateLimiter) limiter;
        ClientState initial = dreb.peekState("u2");
        double burstMax = initial.burstTokens;
        double sustainedMax = initial.sustainedTokens;
        double idleCap = burstMax * 2.0;

        double currentTime = 0.0;
        for (RequestStep step : trace) {
            currentTime += step.dt();
            clock.set(currentTime);
            dreb.allow("u2", step.cost());

            ClientState s = dreb.peekState("u2");
            assertTrue(s.burstTokens >= 0.0, "burstTokens >= 0");
            assertTrue(s.burstTokens <= burstMax + 1e-6, "burstTokens <= burstMax");
            assertTrue(s.sustainedTokens >= 0.0, "sustainedTokens >= 0");
            assertTrue(s.sustainedTokens <= sustainedMax + 1e-6, "sustainedTokens <= sustainedMax");
            assertTrue(s.idleCredit >= 0.0, "idleCredit >= 0");
            assertTrue(s.idleCredit <= idleCap + 1e-6, "idleCredit <= idleCap");
            assertTrue(s.abuseScore >= 0.0, "abuseScore >= 0");
        }
    }

    @Property
    void cumulativeAdmissionBoundHoldsForV1(
            @ForAll("requestTrace") List<RequestStep> trace,
            @ForAll @DoubleRange(min = 10.0, max = 100.0) double capacity,
            @ForAll @DoubleRange(min = 2.0, max = 50.0) double refillRate
    ) {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = Dreb.builder()
                .capacity(capacity)
                .refillRate(refillRate)
                .clock(clock)
                .build();

        AbstractRateLimiter dreb = (AbstractRateLimiter) limiter;
        double sustainedMax = dreb.peekState("c-bound").sustainedTokens;
        double sustainedRate = refillRate;

        double currentTime = 0.0;
        long totalAdmittedCost = 0;

        for (RequestStep step : trace) {
            currentTime += step.dt();
            clock.set(currentTime);
            if (dreb.allow("c-bound", step.cost()) == Decision.ALLOW) {
                totalAdmittedCost += step.cost();
            }
        }

        double maxAllowedBound = sustainedMax + sustainedRate * currentTime + 1e-6;
        assertTrue(totalAdmittedCost <= maxAllowedBound,
                "Admitted cost (" + totalAdmittedCost + ") exceeded cumulative bound (" + maxAllowedBound + ")");
    }

    @Property
    void cumulativeAdmissionBoundHoldsForV2(
            @ForAll("requestTrace") List<RequestStep> trace,
            @ForAll @DoubleRange(min = 10.0, max = 100.0) double capacity,
            @ForAll @DoubleRange(min = 2.0, max = 50.0) double refillRate
    ) {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = Dreb.builderV2()
                .capacity(capacity)
                .refillRate(refillRate)
                .clock(clock)
                .build();

        AbstractRateLimiter dreb = (AbstractRateLimiter) limiter;
        double sustainedMax = dreb.peekState("c2-bound").sustainedTokens;
        double sustainedRate = refillRate;

        double currentTime = 0.0;
        long totalAdmittedCost = 0;

        for (RequestStep step : trace) {
            currentTime += step.dt();
            clock.set(currentTime);
            if (dreb.allow("c2-bound", step.cost()) == Decision.ALLOW) {
                totalAdmittedCost += step.cost();
            }
        }

        double maxAllowedBound = sustainedMax + sustainedRate * currentTime + 1e-6;
        assertTrue(totalAdmittedCost <= maxAllowedBound,
                "V2 admitted cost (" + totalAdmittedCost + ") exceeded cumulative bound (" + maxAllowedBound + ")");
    }

    @Property
    void deniedRequestNeverConsumesTokens(
            @ForAll @IntRange(min = 1, max = 50) int initialDrains,
            @ForAll @IntRange(min = 10, max = 500) int bigCost,
            @ForAll @DoubleRange(min = 0.0, max = 10.0) double dt
    ) {
        ManualClock clock = new ManualClock(0.0);
        DREBLimiter limiter = new DREBLimiter(
                new InMemoryStateStore(), clock,
                new ConstantRateRefillStrategy(10, 5, 20, 5),
                new LinearIdleCreditPolicy(2, 10, 1.5),
                20);

        // Perform some requests to establish a non-trivial state
        for (int i = 0; i < initialDrains; i++) {
            limiter.allow("user-deny", 1);
        }

        // Advance time by dt
        clock.set(100.0 + dt);

        // Sample state before attempting an unaffordable request
        ClientState beforeState = limiter.peekState("user-deny");
        // Predict refilled state without consumption
        double dtRefill = Math.max(0.0, (100.0 + dt) - beforeState.lastRefillTs);
        double burstBefore = beforeState.burstTokens;
        double expectedBurst = Math.min(10.0, burstBefore + dtRefill * 5.0);
        double expectedSustained = Math.min(20.0, beforeState.sustainedTokens + dtRefill * 5.0);

        double timeToFill = (burstBefore >= 10.0) ? 0.0 : ((10.0 - burstBefore) / 5.0);
        double tFull = Math.max(0.0, dtRefill - timeToFill);
        double expectedIdle = Math.min(10.0, beforeState.idleCredit + tFull * 2.0);

        // Attempt a request with cost larger than total available capacity
        Decision decision = limiter.allow("user-deny", 10_000 + bigCost);
        assertEquals(Decision.DENY, decision);

        ClientState afterState = limiter.peekState("user-deny");
        // Buckets should reflect refill but ZERO consumption
        assertEquals(expectedBurst, afterState.burstTokens, 1e-6, "Burst tokens must not be consumed on DENY");
        assertEquals(expectedSustained, afterState.sustainedTokens, 1e-6, "Sustained tokens must not be consumed on DENY");
        assertEquals(expectedIdle, afterState.idleCredit, 1e-6, "Idle credit must not be spent on DENY");
    }

    @Property
    void idleCreditNeverIncreasesWhenBurstBucketNotFull(
            @ForAll @DoubleRange(min = 0.0, max = 30.0) double initialIdleCredit,
            @ForAll @DoubleRange(min = 0.01, max = 5.0) double dtSmall,
            @ForAll @IntRange(min = 1, max = 5) int cost
    ) {
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(20.0, 1.0, 50.0, 1.0);
        LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(10.0, 30.0, 1.5);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50.0);

        // Initialize client state with burst bucket partially empty and arbitrary prior idle credit
        ClientState s0 = store.load("u-notfull", () -> new ClientState(5.0, 50.0, initialIdleCredit, 0.0));
        double creditBefore = s0.idleCredit;

        // At dtSmall <= 5.0 with burstRate=1.0, burstTokens reaches at most 5 + 5 = 10 < 20 (never full)
        clock.set(dtSmall);
        limiter.allow("u-notfull", cost);

        ClientState s1 = limiter.peekState("u-notfull");
        assertTrue(s1.burstTokens < 20.0, "Burst bucket must still be under full capacity");
        assertTrue(s1.idleCredit <= creditBefore + 1e-6,
                "Idle credit must never increase while burst bucket is not full (was: " + creditBefore + ", now: " + s1.idleCredit + ")");
    }
}
