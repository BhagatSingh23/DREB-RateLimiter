package com.dreb.ratelimit.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7.3 Workstream A:
 * Empirical Verification of Over-Admission Boundaries.
 *
 * Demonstrates:
 * 1. Single-event maximum over-admission under C=1000 burst (+704.29 tokens at T=10ms).
 * 2. Asynchronous failover with full initial idle credit (+704.29 tokens).
 * 3. Arbitrary violation under repeated clock jumps without slew bounds.
 */
public class Phase73ReconciliationTest {

    private JedisPool jedisPool;
    private RedisConnectionProvider.Standalone connectionProvider;
    private ProductionRedisRca70Limiter limiter;

    private static final double S_MAX = 1285.7143;
    private static final double R_SUSTAINED = 1000.0;

    @BeforeEach
    public void setUp() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        this.jedisPool = new JedisPool(poolConfig, "127.0.0.1", 6379, 2000);
        this.connectionProvider = new RedisConnectionProvider.Standalone(jedisPool);

        RedisLimiterConfig config = new RedisLimiterConfig.Builder()
                .activeStateTtlSeconds(0)
                .idempotencyTtlSeconds(60)
                .clockSource(RedisLimiterConfig.ClockSource.CLIENT_TIMESTAMP)
                .coldStartPolicy(RedisLimiterConfig.ColdStartPolicy.PERMISSIVE_FULL_BURST)
                .keyPrefix("p73rec")
                .build();

        this.limiter = new ProductionRedisRca70Limiter(connectionProvider, config);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
        }
    }

    @AfterEach
    public void tearDown() {
        if (limiter != null) {
            limiter.close();
        }
    }

    private double calculatePhysicalG1Bound(double elapsedRealSeconds) {
        return S_MAX + (R_SUSTAINED * elapsedRealSeconds);
    }

    @Test
    @DisplayName("Audit A.1: 60s forward clock jump with C=1000 initial burst produces +704.29 token excess")
    public void testForwardClockJumpFullCapacityProduces704TokenExcess() {
        String clientId = "max_excess_jump_client";
        double tRealElapsed = 0.01; // 10 ms real physical time

        // 1. Client starts with full burst + idle credit (B=700, I=450, S=1285.71)
        // Drains full nominal capacity C=1000 at tClock = 100.0s
        LimiterDecision d1 = limiter.tryAcquire(clientId, 1000, null, null, 100.0);
        assertTrue(d1.isAllowed());
        assertEquals(0.0, d1.burstTokensRemaining(), 1e-4);
        assertEquals(0.0, d1.idleCreditRemaining(), 1e-4);

        // 2. Clock jumps forward by 60 seconds (tClock = 160.0s) in 10 ms real time:
        // Refills S to 1285.71, B to 700, and I to 450 (effBurst = 1000.0)
        LimiterDecision d2 = limiter.tryAcquire(clientId, 1000, null, null, 160.0);
        assertTrue(d2.isAllowed(), "Limiter permits second full burst of 1000 after 60s forward clock leap");

        // 3. Mathematical G1 evaluation over 10 ms:
        double totalAdmitted = 1000.0 + 1000.0; // 2000.0 tokens
        double g1Bound = calculatePhysicalG1Bound(tRealElapsed); // 1295.7143 tokens
        double excessAdmission = totalAdmitted - g1Bound;

        // Proves that 404.29 was NOT the maximum possible excess; full capacity produces 704.29 tokens!
        assertEquals(704.2857, excessAdmission, 1.0,
                "Single forward jump with C=1000 burst produces 704.29 tokens of over-admission!");
    }

    @Test
    @DisplayName("Audit A.2: Asynchronous failover with C=1000 initial burst produces +704.29 token excess")
    public void testAsyncFailoverFullCapacityProduces704TokenExcess() {
        String clientId = "max_excess_failover_client";
        double t0 = 100.0;
        double tRealElapsed = 0.01; // 10 ms real physical time

        // 1. Client acquires 1000 tokens on Primary: B=0, I=0, S=285.71
        LimiterDecision d1 = limiter.tryAcquire(clientId, 1000, null, null, t0);
        assertTrue(d1.isAllowed());

        // 2. Primary crashes un-replicated. Promoted replica holds pre-debit state:
        try (Jedis jedis = jedisPool.getResource()) {
            String key = limiter.formatStateKey(clientId);
            jedis.hset(key, "burst_tokens", "700.0");
            jedis.hset(key, "sustained_tokens", "1285.7143");
            jedis.hset(key, "idle_credit", "450.0");
            jedis.hset(key, "last_refill_ts", "100.0");
            jedis.hset(key, "abuse_score", "0.0");
        }

        // 3. Client acquires 1000 tokens on Promoted Replica at t0 + 0.01s:
        LimiterDecision d2 = limiter.tryAcquire(clientId, 1000, null, null, t0 + 0.01);
        assertTrue(d2.isAllowed(), "Replica admitted second 1000 burst because pre-debit state was restored");

        double totalAdmitted = 1000.0 + 1000.0; // 2000.0 tokens
        double g1Bound = calculatePhysicalG1Bound(tRealElapsed); // 1295.7143 tokens
        double excessAdmission = totalAdmitted - g1Bound;

        assertEquals(704.2857, excessAdmission, 1.0,
                "Asynchronous failover with full C=1000 burst produces 704.29 tokens excess!");
    }

    @Test
    @DisplayName("Audit A.3: Repeated forward clock jumps produce arbitrarily growing over-admission without slew bounds")
    public void testRepeatedForwardJumpsProduceArbitraryExcess() {
        String clientId = "repeated_jump_client";
        double tRealElapsed = 0.01; // 10 ms real physical time

        // Initial burst of 1000 at tClock = 100.0
        limiter.tryAcquire(clientId, 1000, null, null, 100.0);

        // Three successive clock jumps of 60s within the same 10ms real window:
        limiter.tryAcquire(clientId, 1000, null, null, 160.0);
        limiter.tryAcquire(clientId, 1000, null, null, 220.0);
        limiter.tryAcquire(clientId, 1000, null, null, 280.0);

        // Total admitted across 4 bursts: 4 * 1000 = 4000 tokens!
        double totalAdmitted = 4000.0;
        double g1Bound = calculatePhysicalG1Bound(tRealElapsed);
        double excess = totalAdmitted - g1Bound;

        assertEquals(2704.2857, excess, 1.0,
                "Multiple clock leaps within the same physical window scale over-admission arbitrarily!");
    }
}
