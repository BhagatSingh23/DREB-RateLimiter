package com.dreb.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class InputValidationAndRollbackReproductionTest {

    @Test
    @DisplayName("P7: cost <= 0 must throw IllegalArgumentException")
    void testNonPositiveCost() {
        RateLimiter limiter = Dreb.builder().capacity(10).refillRate(10).build();
        assertThrows(IllegalArgumentException.class, () -> limiter.allow("user", 0));
        assertThrows(IllegalArgumentException.class, () -> limiter.allow("user", -5));
    }

    @Test
    @DisplayName("P7: NaN or Infinite timestamps from clock must throw IllegalArgumentException")
    void testNonFiniteTimestamps() {
        ManualClock clock = new ManualClock(Double.NaN);
        RateLimiter limiter = Dreb.builder().capacity(10).refillRate(10).clock(clock).build();
        assertThrows(IllegalArgumentException.class, () -> limiter.allow("user", 1));

        clock.set(Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> limiter.allow("user", 1));
    }

    @Test
    @DisplayName("P7: Clock rollback must not corrupt state or grant free tokens when time advances again")
    void testClockRollback() {
        ManualClock clock = new ManualClock(100.0);
        InMemoryStateStore store = new InMemoryStateStore();
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 5, 20, 5);
        LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 10, 1.5);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 20);

        // First call at t=100.0
        assertEquals(Decision.ALLOW, limiter.allow("client1", 10)); // drains all 10 burst tokens
        ClientState s1 = limiter.peekState("client1");
        assertEquals(0.0, s1.burstTokens, 1e-6);
        assertEquals(100.0, s1.lastRefillTs, 1e-6);

        // Clock jumps backward to t=50.0 (rollback)
        clock.set(50.0);
        assertEquals(Decision.DENY, limiter.allow("client1", 1)); // no tokens available

        // Check state: lastRefillTs must NOT be rolled back to 50.0!
        ClientState s2 = limiter.peekState("client1");
        assertTrue(s2.lastRefillTs >= 100.0,
                "lastRefillTs must remain clamped at 100.0, not rolled back to 50.0 (was: " + s2.lastRefillTs + ")");

        // Now advance clock to t=102.0 (only 2.0s forward from the original 100.0)
        // If lastRefillTs was set to 50.0, elapsed time would be 102 - 50 = 52s, granting 52*5 = 260 burst tokens!
        // But true elapsed forward time is only 102 - 100 = 2s, granting 2*5 = 10 burst tokens.
        clock.set(102.0);
        limiter.allow("client1", 1);
        ClientState s3 = limiter.peekState("client1");
        // Refill from 0 at rate 5 for 2s gives 10 tokens. minus 1 cost = 9 tokens.
        assertEquals(9.0, s3.burstTokens, 1e-6,
                "Burst tokens should reflect only 2.0s elapsed forward, not backward clock jump");
    }

    @Test
    @DisplayName("P7: InMemoryStateStore supports TTL eviction of inactive clients")
    void testTtlEviction() {
        InMemoryStateStore store = new InMemoryStateStore(Duration.ofSeconds(60));
        ClientState s = store.load("u1", () -> ClientState.fresh(10, 10, 1000.0));
        assertEquals(1, store.size());

        // Simulated time before TTL
        assertNotNull(store.loadIfPresent("u1"));

        // Evict expired entries after 100s
        store.evictExpired(1100.0);
        assertEquals(0, store.size(), "Client idle beyond TTL should be evicted");
    }
}
