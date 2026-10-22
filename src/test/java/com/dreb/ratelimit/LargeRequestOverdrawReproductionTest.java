package com.dreb.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LargeRequestOverdrawReproductionTest {

    @Test
    @DisplayName("P4 reproduction: a single request of cost > C should not be admitted from full state in burst-matched mapping")
    void testLargeRequestOverdraw() {
        // C = 70, refillRate = 10
        // BURST_CEILING_MATCHED:
        // burstMax = 3C/7 = 30
        // idleCap = 6C/7 = 60
        // sustainedMax = 9C/7 = 90
        // idleDecay = 1.5
        // Intended burst ceiling = burstMax + idleCap/idleDecay = 30 + 60/1.5 = 70.
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = Dreb.builder()
                .capacity(70)
                .refillRate(10)
                .mapping(Dreb.Mapping.BURST_CEILING_MATCHED)
                .clock(clock)
                .build();

        // Touch client at t=0 so lastRefillTs is 0.0
        ((AbstractRateLimiter) limiter).peekState("c1");
        // Advance clock by 100 seconds of idle
        clock.set(100.0);

        // A single request of cost 80 arrives.
        // Refill during these 100s sets burst=30, sustained=90, idleCredit=60.
        // Effective burst limit should be burstMax + idleCap/decay = 30 + 60/1.5 = 70.
        // Cost 80 > 70, so it must be DENIED!
        // Under the buggy code: effectiveBurst = burst(30) + idleCredit(60) = 90 >= 80,
        // so cost 80 was admitted!
        Decision decision = limiter.allow("c1", 80);
        assertEquals(Decision.DENY, decision,
                "Request of cost 80 > capacity 70 must be DENIED, but buggy check admitted it");
    }
}
