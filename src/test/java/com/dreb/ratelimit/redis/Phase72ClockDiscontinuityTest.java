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
 * Phase 7.2 Workstream D:
 * Forward & Backward Clock Discontinuity and Physical Time G1 Audit.
 *
 * Evaluates whether maximum bucket caps alone prevent G1 envelope violations
 * when Redis server or client timestamps jump ahead of physical elapsed time T.
 */
public class Phase72ClockDiscontinuityTest {

    private JedisPool jedisPool;
    private RedisConnectionProvider.Standalone connectionProvider;
    private ProductionRedisRca70Limiter limiter;

    private static final double S_MAX = 1285.7143;
    private static final double R_SUSTAINED = 1000.0;
    private static final double B_MAX = 700.0;

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
                .coldStartPolicy(RedisLimiterConfig.ColdStartPolicy.SECURE_CONSERVATIVE)
                .keyPrefix("p72clock")
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

    /**
     * Physical time bound according to G1:
     * A_bound(T) = S_max + R * T_real
     */
    private double calculatePhysicalG1Bound(double elapsedRealSeconds) {
        return S_MAX + (R_SUSTAINED * elapsedRealSeconds);
    }

    @Test
    @DisplayName("Audit D.1: Forward clock jump of 1 second violates physical G1 bound by 104.29 tokens")
    public void testForwardClockJump1Second() {
        String clientId = "forward_jump_1s_client";
        double tReal0 = 0.0;
        double tReal1 = 0.01; // 10 ms real physical time has passed
        double tRealElapsed = tReal1 - tReal0;

        // 1. Initial burst at tClock = 100.0s (real t=0.0)
        LimiterDecision d1 = limiter.tryAcquire(clientId, 700, null, null, 100.0);
        assertTrue(d1.isAllowed());
        assertEquals(0.0, d1.burstTokensRemaining(), 1e-4);

        // 2. Clock jumps forward by 1.0 second: tClock = 101.0s (even though real physical elapsed time is only 10ms)
        LimiterDecision d2 = limiter.tryAcquire(clientId, 700, null, null, 101.0);
        assertTrue(d2.isAllowed(), "Limiter refilled burst capacity because clock jumped forward by 1s!");

        // 3. Compare admissions against PHYSICAL time:
        double totalAdmitted = 700.0 + 700.0; // 1400.0 tokens
        double physicalBound = calculatePhysicalG1Bound(tRealElapsed); // 1285.71 + 1000 * 0.01 = 1295.71
        double excessAdmission = totalAdmitted - physicalBound;

        // Bucket caps (B_max=700) alone DID NOT prevent G1 violation over physical time!
        assertTrue(excessAdmission > 0, "G1 bound over physical time MUST be violated during forward clock jump");
        assertEquals(104.2857, excessAdmission, 1.0,
                "Excess admission over physical time matches lost debit envelope!");
    }

    @Test
    @DisplayName("Audit D.2: Forward clock jump of 10 seconds violates physical G1 bound")
    public void testForwardClockJump10Seconds() {
        String clientId = "forward_jump_10s_client";
        double tRealElapsed = 0.01; // 10 ms physical time

        // Initial burst
        LimiterDecision d1 = limiter.tryAcquire(clientId, 700, null, null, 100.0);
        assertTrue(d1.isAllowed());

        // Clock jumps forward by 10.0 seconds (tClock = 110.0s)
        LimiterDecision d2 = limiter.tryAcquire(clientId, 700, null, null, 110.0);
        assertTrue(d2.isAllowed());

        double totalAdmitted = 1400.0;
        double physicalBound = calculatePhysicalG1Bound(tRealElapsed);
        double excessAdmission = totalAdmitted - physicalBound;

        assertTrue(excessAdmission > 0);
        assertEquals(104.2857, excessAdmission, 1.0);
    }

    @Test
    @DisplayName("Audit D.3: Forward clock jump of 60 seconds restores idle credit and violates physical G1 bound by 404.29 tokens")
    public void testForwardClockJump60Seconds() {
        String clientId = "forward_jump_60s_client";
        double tRealElapsed = 0.01; // 10 ms physical time

        // Initial burst of 700 tokens at tClock = 100.0s
        LimiterDecision d1 = limiter.tryAcquire(clientId, 700, null, null, 100.0);
        assertTrue(d1.isAllowed());

        // Clock jumps forward by 60.0 seconds (tClock = 160.0s):
        // This not only refills B=700, but fills idle credit to I_max=450 (effBurst = 700 + 450/1.5 = 1000)!
        LimiterDecision d2 = limiter.tryAcquire(clientId, 1000, null, null, 160.0);
        assertTrue(d2.isAllowed(), "Limiter allowed full 1000 burst because 60s jump accrued maximum idle credit!");

        double totalAdmitted = 700.0 + 1000.0; // 1700.0 tokens admitted in 10 ms physical time!
        double physicalBound = calculatePhysicalG1Bound(tRealElapsed); // 1295.71 tokens
        double excessAdmission = totalAdmitted - physicalBound;

        assertTrue(excessAdmission > 0);
        assertEquals(404.2857, excessAdmission, 1.0,
                "Massive forward jump permits full burst + idle credit harvest, producing 404.29 token violation!");
    }

    @Test
    @DisplayName("Audit D.4: Forward jump followed by immediate request burst drains bucket and clamps to capacity caps")
    public void testForwardJumpFollowedByImmediateBurst() {
        String clientId = "burst_after_jump_client";

        // Initial state at tClock = 100.0s
        limiter.tryAcquire(clientId, 700, null, null, 100.0);

        // Forward jump by 10s: refills B=700, plus idle credit I=450 (effBurst = 700 + 300 = 1000)
        // Burst 1: cost 600 (remaining available burst = 400)
        LimiterDecision b1 = limiter.tryAcquire(clientId, 600, null, null, 110.0);
        assertTrue(b1.isAllowed());

        // Burst 2: cost 600 immediately after (same timestamp)
        LimiterDecision b2 = limiter.tryAcquire(clientId, 600, null, null, 110.0);
        assertFalse(b2.isAllowed(), "Capacity caps (effBurst=1000 < 1200) prevent unbounded debit within the jumped timestamp");
        assertEquals("BURST_LIMIT_EXCEEDED", b2.rejectionReason());
    }

    @Test
    @DisplayName("Audit D.5: Backward clock jump clamps dt=0, preserving balances but freezing refills for physical duration")
    public void testBackwardJumpFollowedByGradualRecovery() {
        String clientId = "backward_jump_recovery_client";

        // Client debits 700 tokens at tClock = 100.0s: B=0, S=585.71
        limiter.tryAcquire(clientId, 700, null, null, 100.0);

        // Clock jumps backward by 10.0s to tClock = 90.0s:
        // Client attempts to acquire 50 tokens at tClock = 90.0s:
        LimiterDecision dFrozen1 = limiter.tryAcquire(clientId, 50, null, null, 90.0);
        assertFalse(dFrozen1.isAllowed(), "Tokens cannot refill because elapsed time is clamped to dt=0");

        // Real physical time advances by 5 seconds (tClock advances to 95.0s, still <= 100.0s):
        LimiterDecision dFrozen2 = limiter.tryAcquire(clientId, 50, null, null, 95.0);
        assertFalse(dFrozen2.isAllowed(), "Still frozen because tClock (95) has not reached lastTs (100)");

        // Real physical time advances past original high-water mark: tClock = 101.0s (dt = 1.0s):
        LimiterDecision dRecovered = limiter.tryAcquire(clientId, 50, null, null, 101.0);
        assertTrue(dRecovered.isAllowed(), "Refill resumes once clock advances beyond the original high-water mark!");
        assertEquals(650.0, dRecovered.burstTokensRemaining(), 1.0);
    }
}
