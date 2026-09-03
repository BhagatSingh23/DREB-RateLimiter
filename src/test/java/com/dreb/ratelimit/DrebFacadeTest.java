package com.dreb.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Dreb facade (builder-based public API)")
public class DrebFacadeTest {

    @Test
    @DisplayName("Basic usage (frozen clock): exact count matches PROPORTIONAL mapping's burstMax floor")
    void testBasicUsage() {
        // Uses a ManualClock pinned to a single instant so the result is exactly
        // deterministic -- SystemClock would let a tiny amount of real time (and
        // therefore real refill) pass between each of these 50 rapid calls, making an
        // exact-count assertion flaky. That's expected real-clock behavior, not a bug;
        // see testBasicUsageWithRealClock below for the non-deterministic version.
        ManualClock clock = new ManualClock(1000.0);
        RateLimiter limiter = Dreb.builder().capacity(100).refillRate(100).clock(clock).build();
        int accepted = 0;
        for (int i = 0; i < 50; i++) {
            if (limiter.allow("user-1") == Decision.ALLOW) accepted++;
        }
        // capacity=100, PROPORTIONAL mapping -> burstMax=100/3=33.33, floor=33 whole-token
        // accepts from a cold client with no prior idle credit, clock frozen at one instant.
        assertEquals(33, accepted, "Expected exactly 33/50 accepted");
    }

    @Test
    @DisplayName("Basic usage (real clock): loose bounds, proves end-to-end SystemClock path works")
    void testBasicUsageWithRealClock() {
        // Same setup but with the default SystemClock, to prove the public API works
        // end-to-end with real time too. Loose bounds only -- exact count legitimately
        // varies run to run depending on how much real time elapses during the loop.
        RateLimiter limiter = Dreb.builder().capacity(100).refillRate(100).build();
        int accepted = 0;
        for (int i = 0; i < 50; i++) {
            if (limiter.allow("user-2") == Decision.ALLOW) accepted++;
        }
        assertTrue(accepted >= 33 && accepted <= 50,
                "Unexpected real-clock acceptance: " + accepted);
    }

    @Test
    @DisplayName("BURST_CEILING_MATCHED allows more instantaneous burst than PROPORTIONAL")
    void testBothMappings() {
        ManualClock clock1 = new ManualClock(0.0);
        ManualClock clock2 = new ManualClock(0.0);
        RateLimiter prop = Dreb.builder().capacity(100).refillRate(100)
                .mapping(Dreb.Mapping.PROPORTIONAL).clock(clock1).build();
        RateLimiter burstMatched = Dreb.builder().capacity(100).refillRate(100)
                .mapping(Dreb.Mapping.BURST_CEILING_MATCHED).clock(clock2).build();

        int propAccepted = 0, bmAccepted = 0;
        for (int i = 0; i < 50; i++) {
            if (prop.allow("u") == Decision.ALLOW) propAccepted++;
            if (burstMatched.allow("u") == Decision.ALLOW) bmAccepted++;
        }
        assertTrue(bmAccepted > propAccepted,
                "Expected burst-ceiling-matched (" + bmAccepted +
                ") > proportional (" + propAccepted + ")");
    }

    @Test
    @DisplayName("V2 builder produces a working limiter that accepts requests")
    void testV2() {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter v2 = Dreb.builderV2().capacity(100).refillRate(100).clock(clock).build();
        int accepted = 0;
        for (int i = 0; i < 50; i++) {
            if (v2.allow("u") == Decision.ALLOW) accepted++;
        }
        assertTrue(accepted > 0, "v2 accepted nothing, something is broken");
    }

    @Test
    @DisplayName("Idle time rewards a client with more burst than a cold-start client")
    void testDeterministicClock() {
        // Note: this uses the facade's PROPORTIONAL mapping, which derives burstMax = capacity/3.
        // It does NOT reproduce the paper's Table 4 numbers directly (those used a raw
        // burstMax=20 constant, not derived through this capacity-mapping facade at all) --
        // an earlier version of this test wrongly assumed it would and asserted 30/30. The
        // property that actually generalizes regardless of mapping is qualitative: idle time
        // should let a client accept a larger fraction of a burst than the same client with
        // zero idle history. Verify that instead.
        ManualClock clock = new ManualClock(0.0);

        RateLimiter idled = Dreb.builder().capacity(20).refillRate(5).clock(clock).build();
        clock.set(0.0);
        idled.allow("idled-client", 0); // touch at t=0
        clock.set(60.0); // idle 60s before the burst
        int idledAccepted = 0;
        for (int i = 0; i < 30; i++) {
            clock.set(60.0 + i * 0.05);
            if (idled.allow("idled-client") == Decision.ALLOW) idledAccepted++;
        }

        ManualClock clock2 = new ManualClock(0.0);
        RateLimiter cold = Dreb.builder().capacity(20).refillRate(5).clock(clock2).build();
        int coldAccepted = 0;
        for (int i = 0; i < 30; i++) {
            clock2.set(i * 0.05); // burst starts immediately, no idle time at all
            if (cold.allow("cold-client") == Decision.ALLOW) coldAccepted++;
        }

        assertTrue(idledAccepted > coldAccepted,
                "Idle-then-burst (" + idledAccepted + ") should exceed cold-start (" + coldAccepted + ")");
    }

    @Test
    @DisplayName("Missing capacity in builder throws IllegalStateException")
    void testValidation() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            Dreb.builder().refillRate(10).build(); // capacity never set
        });
        // We just ensure it throws, verifying validation rejected missing capacity
        assertNotNull(exception.getMessage());
    }

    @Test
    @DisplayName("V2 with custom tuning builds and accepts requests")
    void testV2WithCustomTuning() {
        ManualClock clock = new ManualClock(0.0);
        RateLimiter v2 = Dreb.builderV2()
                .capacity(100).refillRate(100)
                .tuning(1.0, 0.5, 3.0, 2.0, 0.5)
                .clock(clock)
                .build();
        assertEquals(Decision.ALLOW, v2.allow("tuned-client"),
                "V2 with custom tuning should accept initial request");
    }

    @Test
    @DisplayName("Custom StateStore is actually used by the limiter")
    void testCustomStateStore() {
        InMemoryStateStore store = new InMemoryStateStore();
        ManualClock clock = new ManualClock(0.0);
        RateLimiter limiter = Dreb.builder().capacity(10).refillRate(10)
                .store(store).clock(clock).build();

        assertEquals(0, store.size(), "No clients tracked before first request");
        limiter.allow("test-user");
        assertEquals(1, store.size(), "Client should be tracked after first request");
    }
}
