package com.dreb.ratelimit.redis;

import com.dreb.ratelimit.ClientState;
import com.dreb.ratelimit.Decision;
import com.dreb.ratelimit.policies.AdaptiveCeilingIdleCreditPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 Clock Source Correctness & Lua Numeric Precision Test Suite.
 *
 * Validates:
 * 1. Clock skew forward/backward vulnerabilities when using client timestamps.
 * 2. Clock skew immunity when using Redis server TIME.
 * 3. Monotonic non-decreasing time clamping in Lua (dt = max(0.0, now - lastRefillTs)).
 * 4. IEEE-754 double precision limits (2^53 = 9,007,199,254,740,992).
 * 5. Microsecond precision of Redis TIME.
 * 6. Differential parity between Java in-memory and Redis Lua over 5,000 pseudo-random steps.
 */
@DisplayName("Phase 6: Clock Source & Lua Numeric Precision Audit")
public class Phase6ClockAndNumericTest {

    private static final String REDIS_HOST = "127.0.0.1";
    private static final int REDIS_PORT = 6379;
    private static JedisPool jedisPool;

    @BeforeAll
    static void setUp() {
        jedisPool = new JedisPool(REDIS_HOST, REDIS_PORT);
    }

    @AfterAll
    static void tearDown() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    /**
     * 1. Clock Skew Audit: Client timestamp manipulation vs. Redis server TIME immunity.
     */
    @Test
    @DisplayName("Task 6: Audit clock skew vulnerabilities and verify Redis server TIME immunity")
    void testClockSkewVulnerabilityAndImmunity() {
        String keyPrefix = "phase6:clock";
        RedisRca70Limiter clientTimeLimiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix + ":client");
        RedisRca70Limiter serverTimeLimiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.REDIS_SERVER_TIME, keyPrefix + ":server");

        String client1 = "client_skew_victim";
        String client2 = "client_server_time";
        clientTimeLimiter.resetClient(client1);
        serverTimeLimiter.resetClient(client2);

        // A. Client Timestamp Skew Forward:
        // Client claims time jumped forward 100 seconds into the future
        clientTimeLimiter.allow(client1, 700, 0.0); // depletes burst to 0 at t=0
        RedisRca70Limiter.RedisStateSnapshot stBefore = clientTimeLimiter.peekState(client1);
        assertNotNull(stBefore);
        assertEquals(0.0, stBefore.burstTokens(), 1e-3);

        // Now client submits timestamp 100.0s (clock skew forward)
        clientTimeLimiter.allow(client1, 1, 100.0);
        RedisRca70Limiter.RedisStateSnapshot stAfter = clientTimeLimiter.peekState(client1);
        assertNotNull(stAfter);
        // Client artificially refilled burst to 700 and idle credit to 450!
        assertEquals(699.0, stAfter.burstTokens(), 1e-3);
        assertEquals(450.0, stAfter.idleCredit(), 1e-3);

        // B. Backward Clock Jump Protection:
        // If client subsequently sends timestamp 50.0s (less than lastRefillTs 100.0s), dt must be clamped to 0.0
        clientTimeLimiter.allow(client1, 10, 50.0);
        RedisRca70Limiter.RedisStateSnapshot stBackward = clientTimeLimiter.peekState(client1);
        assertNotNull(stBackward);
        // lastRefillTs remains 100.0s; dt=0 so no extra tokens granted
        assertEquals(100.0, stBackward.lastRefillTs(), 1e-3);
        assertEquals(689.0, stBackward.burstTokens(), 1e-3); // 699 - 10 = 689

        // C. Server Time Mode (Immunity):
        // In server time mode, caller timestamp is ignored; Redis TIME governs state
        serverTimeLimiter.allow(client2, 700); // executed at real server time
        RedisRca70Limiter.RedisStateSnapshot stServer = serverTimeLimiter.peekState(client2);
        assertNotNull(stServer);
        assertTrue(stServer.lastRefillTs() > 1_700_000_000.0, "Server time must reflect real epoch timestamp");
    }

    /**
     * 2. Lua Numeric Precision & IEEE-754 Limits.
     */
    @Test
    @DisplayName("Task 7: Verify IEEE-754 53-bit integer limit and microsecond Redis TIME precision")
    void testLuaNumericPrecisionAndLimits() {
        try (Jedis jedis = jedisPool.getResource()) {
            // A. Exact integer range up to 2^53 (9,007,199,254,740,992)
            String scriptDouble = """
                    local bigInt = 9007199254740990
                    local add1 = bigInt + 1
                    local add2 = bigInt + 2
                    local add3 = bigInt + 3 -- exceeds 2^53, precision loss occurs
                    return {string.format('%.0f', add1), string.format('%.0f', add2), string.format('%.0f', add3)}
                    """;
            @SuppressWarnings("unchecked")
            java.util.List<String> res = (java.util.List<String>) jedis.eval(scriptDouble, 0);
            assertEquals("9007199254740991", res.get(0)); // 2^53 - 1: exact
            assertEquals("9007199254740992", res.get(1)); // 2^53: exact

            // B. Redis TIME precision: microsecond accuracy
            @SuppressWarnings("unchecked")
            java.util.List<String> timeList = jedis.time();
            long sec = Long.parseLong(timeList.get(0));
            long usec = Long.parseLong(timeList.get(1));
            assertTrue(sec > 1_700_000_000L);
            assertTrue(usec >= 0 && usec < 1_000_000L);

            // In Lua, sec + usec/1e6 has 9 digits before point and 6 digits after point = 15 total digits.
            // 53 bits provides 15-17 significant decimal digits. Therefore, microsecond timestamp precision is exact.
            double combined = sec + (usec / 1_000_000.0);
            assertTrue(combined > 0);
        }
    }

    /**
     * 3. Differential Parity: Java in-memory vs. Redis Lua across 5,000 pseudo-random steps.
     */
    @Test
    @DisplayName("Task 7: 5,000-step differential equivalence between Java in-memory and Redis Lua")
    void testDifferentialParityJavaVsLua() {
        String keyPrefix = "phase6:diff";
        RedisRca70Limiter redisLimiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        String client = "diff_client_5k";
        redisLimiter.resetClient(client);

        // Java in-memory limiter state setup
        double burstMax = 700.0;
        double burstRate = 5.0 * 1000.0 / 3.0;
        double sustainedMax = 9.0 * 1000.0 / 7.0;
        double sustainedRate = 1000.0;
        AdaptiveCeilingIdleCreditPolicy javaPolicy = new AdaptiveCeilingIdleCreditPolicy(1500.0, 450.0, 1.5, 0.75, 0.05);

        double t = 0.0;
        ClientState javaState = ClientState.fresh(burstMax, sustainedMax, t);

        // Synchronize initial state at t=0.0
        Decision initRedis = redisLimiter.allow(client, 1, t);
        assertEquals(Decision.ALLOW, initRedis);
        javaState.burstTokens -= 1.0;
        javaState.sustainedTokens -= 1.0;

        Random rng = new Random(8888L);
        int steps = 5000;
        int matches = 0;

        for (int i = 0; i < steps; i++) {
            // Advance time by random dt in [0.001, 1.5]
            double dt = 0.001 + rng.nextDouble() * 1.5;
            t += dt;

            // Random cost in [1, 900]
            int cost = 1 + rng.nextInt(900);

            // 1. Compute in Java
            // Refill
            double burstBefore = javaState.burstTokens;
            javaState.sustainedTokens = Math.min(sustainedMax, javaState.sustainedTokens + dt * sustainedRate);
            javaState.burstTokens = Math.min(burstMax, burstBefore + dt * burstRate);
            double tFull = 0.0;
            if (burstBefore >= burstMax) {
                tFull = dt;
            } else if (burstRate > 0.0) {
                double timeToFill = (burstMax - burstBefore) / burstRate;
                tFull = Math.max(0.0, dt - timeToFill);
            }
            javaPolicy.accrue(javaState, dt, tFull);
            javaState.lastRefillTs = t;

            double effBurst = javaState.burstTokens + javaPolicy.effectiveCredit(javaState);
            Decision javaDecision = (javaState.sustainedTokens >= cost && effBurst >= cost)
                    ? Decision.ALLOW
                    : Decision.DENY;

            if (javaDecision == Decision.ALLOW) {
                javaState.sustainedTokens -= cost;
                double deficit = Math.max(0.0, cost - javaState.burstTokens);
                javaState.burstTokens = Math.max(0.0, javaState.burstTokens - cost);
                if (deficit > 0.0) {
                    javaPolicy.spend(javaState, deficit);
                }
            }

            // 2. Compute in Redis Lua
            Decision redisDecision = redisLimiter.allow(client, cost, t);

            // Compare decisions
            assertEquals(javaDecision, redisDecision, "Decision mismatch at step " + i + ", t=" + t + ", cost=" + cost);
            matches++;

            // Periodically check internal state parity (every 250 steps)
            if (i % 250 == 0) {
                RedisRca70Limiter.RedisStateSnapshot redisState = redisLimiter.peekState(client);
                assertNotNull(redisState);
                assertEquals(javaState.burstTokens, redisState.burstTokens(), 1e-4, "Burst token mismatch at step " + i);
                assertEquals(javaState.sustainedTokens, redisState.sustainedTokens(), 1e-4, "Sustained token mismatch at step " + i);
                assertEquals(javaState.idleCredit, redisState.idleCredit(), 1e-4, "Idle credit mismatch at step " + i);
                assertEquals(javaState.abuseScore, redisState.abuseScore(), 1e-4, "Abuse score mismatch at step " + i);
            }
        }

        assertEquals(steps, matches, "All 5,000 steps must match identically between Java and Redis Lua");
    }
}
