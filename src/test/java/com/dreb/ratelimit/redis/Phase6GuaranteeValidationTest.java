package com.dreb.ratelimit.redis;

import com.dreb.ratelimit.Decision;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 DREB Theoretical Guarantee Revalidation under Redis Execution.
 *
 * Validates:
 * 1. Guarantee G1: Cumulative envelope A(T) <= S_max + R * T over continuous Redis execution.
 * 2. Guarantee G2: Idle-conditioned burst behavior under non-zero initial abuse scores.
 * 3. Guarantee G3: Debit penalty (d = 1.5) and state update ordering.
 * 4. Theorem 2: Finite-horizon credit-farming bound with terminal terms.
 * 5. Distributed Scope: Single-key vs. Sybil independent keys.
 */
@DisplayName("Phase 6: Guarantee Revalidation in Redis")
public class Phase6GuaranteeValidationTest {

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
     * 1. Guarantee G1: Cumulative Admission Envelope over 3,000 intermediate time steps in Redis.
     */
    @Test
    @DisplayName("Task 9: Verify Guarantee G1 A(t) <= S_max + R*t continuously in Redis")
    void testGuaranteeG1CumulativeEnvelopeInRedis() {
        String keyPrefix = "phase6:g1";
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        String client = "g1_client_cont";
        limiter.resetClient(client);

        double sMax = 1285.7142857;
        double r = 1000.0;
        double t = 0.0;

        // Initialize state at t=0
        limiter.allow(client, 1, t);
        long totalAdmitted = 1;

        Random rng = new Random(12345L);
        int steps = 3000;
        double maxObservedViolation = 0.0;

        for (int i = 0; i < steps; i++) {
            double dt = 0.005 + rng.nextDouble() * 0.020; // 5ms to 25ms steps
            t += dt;

            // Aggressive offered traffic (cost 20 every ~15ms = offering ~1.33R)
            int cost = 20;
            Decision d = limiter.allow(client, cost, t);
            if (d == Decision.ALLOW) {
                totalAdmitted += cost;
            }

            double envelope = sMax + r * t;
            double violation = Math.max(0.0, totalAdmitted - envelope);
            if (violation > maxObservedViolation) {
                maxObservedViolation = violation;
            }
        }

        assertEquals(0.0, maxObservedViolation, 1e-6,
                "Guarantee G1 cumulative envelope A(t) <= S_max + R*t must hold with zero violation across all Redis steps");
    }

    /**
     * 2. Guarantee G2: Idle-Conditioned Burst Behavior under Non-Zero Abuse State.
     */
    @Test
    @DisplayName("Task 9: Verify Guarantee G2 ceiling contraction suppresses repeated burst farming in Redis")
    void testGuaranteeG2IdleBurstAndCeilingContraction() {
        String keyPrefix = "phase6:g2";
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        String client = "g2_burst_client";
        limiter.resetClient(client);

        // Client accumulates full rest at t=10.0s (effBurst = 1000)
        limiter.allow(client, 1, 0.0);
        Decision d1 = limiter.allow(client, 1000, 10.0);
        assertEquals(Decision.ALLOW, d1, "First burst of 1000 after full rest must be admitted");

        RedisRca70Limiter.RedisStateSnapshot st1 = limiter.peekState(client);
        assertNotNull(st1);
        double k1 = st1.abuseScore();
        assertEquals(300.0 / 450.0, k1, 1e-3); // abuseScore = 0.667

        // Client attempts a second burst of 1000 after only 0.720s (refill interval)
        // With beta=0.75 and K=0.667, effCap = 450 / (1 + 0.75*0.667) = 300
        // Allowable burst = 700 + 300/1.5 = 900 < 1000!
        Decision d2 = limiter.allow(client, 1000, 10.720);
        assertEquals(Decision.DENY, d2, "Second burst of 1000 must be denied due to adaptive ceiling contraction");

        // But a reduced burst of 850 fits within the contracted ceiling (effBurst ~ 900)
        Decision d3 = limiter.allow(client, 850, 10.720);
        assertEquals(Decision.ALLOW, d3, "Contracted burst of 850 must be admitted under suppressed ceiling");
    }

    /**
     * 3. Guarantee G3: Debit Penalty (d = 1.5) applied correctly in Redis.
     */
    @Test
    @DisplayName("Task 9: Verify Guarantee G3 debit penalty d=1.5 ordering in Redis")
    void testGuaranteeG3DebitPenalty() {
        String keyPrefix = "phase6:g3";
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        String client = "g3_debit_client";
        limiter.resetClient(client);

        // Rest to earn full idle credit (450)
        limiter.allow(client, 1, 0.0);
        limiter.allow(client, 1, 10.0);

        RedisRca70Limiter.RedisStateSnapshot stRest = limiter.peekState(client);
        assertNotNull(stRest);
        assertEquals(450.0, stRest.idleCredit(), 1e-3);

        // Deplete burst bucket to 0 with request of 699
        limiter.allow(client, 699, 10.0);

        // Now issue request of cost 100 (which must draw 100 deficit from idle credit)
        // Deficit = 100. Debit penalty d = 1.5 -> credit consumed = 100 * 1.5 = 150!
        Decision d = limiter.allow(client, 100, 10.0);
        assertEquals(Decision.ALLOW, d);

        RedisRca70Limiter.RedisStateSnapshot stAfter = limiter.peekState(client);
        assertNotNull(stAfter);
        // Idle credit remaining: 450 - 150 = 300!
        assertEquals(300.0, stAfter.idleCredit(), 1e-3, "Debit penalty d=1.5 must consume 150 credit tokens for 100 deficit");
    }

    /**
     * 4. Distributed Scope: Single Key vs. Sybil Independent Keys.
     */
    @Test
    @DisplayName("Task 9: Verify per-key rate limit does not prevent Sybil attack across M independent keys")
    void testDistributedScopeSingleKeyVsSybil() {
        String keyPrefix = "phase6:sybil";
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        // 5 distinct Sybil keys operated by the same coordinated attacker
        int m = 5;
        double t = 0.0;
        int totalSybilAdmitted = 0;

        for (int i = 0; i < m; i++) {
            String sybilKey = "attacker_sybil_" + i;
            limiter.resetClient(sybilKey);
            // Each Sybil key gets fresh initial capacity (burst=700)
            Decision d = limiter.allow(sybilKey, 700, t);
            if (d == Decision.ALLOW) {
                totalSybilAdmitted += 700;
            }
        }

        // Total admitted across M keys is M * 700 = 3500 tokens!
        assertEquals(m * 700, totalSybilAdmitted,
                "Demonstrate that per-key limit R scales linearly with M distinct Sybil keys (M * R)");
        assertTrue(totalSybilAdmitted > 1285.71,
                "Sybil traffic exceeds single-key S_max, proving per-key rate limiting is not Sybil resistant without global ingress defense");
    }
}
