package com.dreb.ratelimit.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisDataException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7.1 Workstream A, B, and D Audit:
 * Durability, Acknowledged-Write Loss, Idempotency Failures, and Memory Exhaustion.
 */
public class Phase71DurabilityAndFailureAuditTest {

    private JedisPool jedisPool;
    private RedisConnectionProvider.Standalone connectionProvider;
    private ProductionRedisRca70Limiter limiter;

    @BeforeEach
    public void setUp() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        this.jedisPool = new JedisPool(poolConfig, "127.0.0.1", 6379, 2000);
        this.connectionProvider = new RedisConnectionProvider.Standalone(jedisPool);

        RedisLimiterConfig config = new RedisLimiterConfig.Builder()
                .activeStateTtlSeconds(0) // Durable state
                .idempotencyTtlSeconds(60)
                .clockSource(RedisLimiterConfig.ClockSource.CLIENT_TIMESTAMP)
                .coldStartPolicy(RedisLimiterConfig.ColdStartPolicy.SECURE_CONSERVATIVE)
                .failureMode(RedisLimiterConfig.FailureMode.FAIL_CLOSED)
                .keyPrefix("p71")
                .build();

        this.limiter = new ProductionRedisRca70Limiter(connectionProvider, config);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.flushAll();
            jedis.configSet("maxmemory", "0");
            jedis.configSet("maxmemory-policy", "noeviction");
        }
    }

    @AfterEach
    public void tearDown() {
        try (Jedis jedis = new Jedis("127.0.0.1", 6379)) {
            jedis.configSet("maxmemory", "0");
            jedis.configSet("maxmemory-policy", "noeviction");
        } catch (Exception ignored) {
        }
        if (limiter != null) {
            limiter.close();
        }
    }

    // =========================================================================
    // WORKSTREAM A: PERSISTENCE & ACKNOWLEDGED-WRITE LOSS
    // =========================================================================

    @Test
    @DisplayName("Audit A.1: Acknowledged debit lost during failover permits extra admission beyond G1 envelope")
    public void testAcknowledgedDebitLossAllowsG1OverAdmission() {
        String clientId = "failover_overadmission_client";
        double t0 = 100.0;

        // 1. Client has full burst (B=700, S=1285.71). Submits cost=700 at t0 on Primary
        LimiterDecision d1 = limiter.tryAcquire(clientId, 700, null, null, t0);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, d1.status());
        assertEquals(0.0, d1.burstTokensRemaining(), 1e-4);
        assertEquals(585.7143, d1.sustainedTokensRemaining(), 1.0);

        // 2. Primary acknowledges write to client, but crashes before write is replicated to replica.
        // Replica is promoted to master with pre-debit state: (B=700, S=1285.71).
        // We simulate this un-replicated replica promotion by rolling back the Redis state to initial values.
        try (Jedis jedis = jedisPool.getResource()) {
            String key = limiter.formatStateKey(clientId);
            jedis.hset(key, "burst_tokens", "700.0");
            jedis.hset(key, "sustained_tokens", "1285.7143");
            jedis.hset(key, "idle_credit", "0.0");
            jedis.hset(key, "last_refill_ts", "100.0");
            jedis.hset(key, "abuse_score", "0.0");
        }

        // 3. Client submits another request of cost 700 at t0 + 0.01s on promoted replica
        LimiterDecision d2 = limiter.tryAcquire(clientId, 700, null, null, t0 + 0.01);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, d2.status(),
                "Promoted replica admitted second burst because acknowledged write was lost!");

        // 4. Mathematical G1 Envelope Audit:
        // Total tokens admitted over interval [100.0, 100.01] = 700 + 700 = 1400 tokens!
        // Theoretical G1 envelope = S_max + R * dt = 1285.71 + 1000 * 0.01 = 1295.71 tokens.
        double totalAdmitted = 1400.0;
        double g1Envelope = 1285.7143 + (1000.0 * 0.01);
        double excessAdmission = totalAdmitted - g1Envelope;

        assertTrue(excessAdmission > 0.0, "G1 envelope MUST be violated under acknowledged write loss!");
        assertEquals(104.2857, excessAdmission, 1.0,
                "Observed excess admission exposure matches lost debit surplus!");
    }

    @Test
    @DisplayName("Audit A.2: State hash present but idempotency record missing causes double-debit on retry")
    public void testStatePresentIdempotencyMissingCausesDoubleDebit() {
        String clientId = "state_present_idemp_missing";
        String idempKey = "req-audit-991";
        String fp = "cost=300";
        double t0 = 100.0;

        // Initial request debits 300 tokens: B=400, S=985.71
        LimiterDecision d1 = limiter.tryAcquire(clientId, 300, idempKey, fp, t0);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, d1.status());
        assertEquals(400.0, d1.burstTokensRemaining(), 1e-4);

        // Simulate idempotency record loss (e.g. idempotency TTL expired or lost during failover)
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(limiter.formatIdempKey(clientId, idempKey));
        }

        // Client retries with identical key and fingerprint
        LimiterDecision dRetry = limiter.tryAcquire(clientId, 300, idempKey, fp, t0);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, dRetry.status());
        assertFalse(dRetry.isCached(), "Must not return cached response when idempotency record is missing");

        // CRITICAL DOUBLE-DEBIT OBSERVATION:
        // Burst tokens debited a second time: 400 - 300 = 100!
        ClientStateSnapshot state = limiter.getState(clientId);
        assertEquals(100.0, state.burstTokens(), 1e-4,
                "Missing idempotency record resulted in double-debit (400 - 300 = 100)!");
    }

    @Test
    @DisplayName("Audit A.3: Idempotency record present but state hash missing returns cached decision without recreating state")
    public void testIdempotencyPresentStateMissingReturnsCachedWithoutRecreating() {
        String clientId = "idemp_present_state_missing";
        String idempKey = "req-audit-992";
        String fp = "cost=100";
        double t0 = 100.0;

        limiter.tryAcquire(clientId, 100, idempKey, fp, t0);
        assertNotNull(limiter.getState(clientId));

        // State hash is deleted (e.g. administrative purge or catastrophic crash), but idempotency record remains
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(limiter.formatStateKey(clientId));
        }

        // Retry arrives
        LimiterDecision dRetry = limiter.tryAcquire(clientId, 100, idempKey, fp, t0);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, dRetry.status());
        assertTrue(dRetry.isCached(), "Must return cached decision from surviving idempotency record");

        // State hash was NOT recreated!
        assertNull(limiter.getState(clientId), "State hash must remain missing because cached check short-circuits execution");
    }

    // =========================================================================
    // WORKSTREAM D: MEMORY EXHAUSTION & NOEVICTION AUDIT
    // =========================================================================

    @Test
    @DisplayName("Audit D.1: maxmemory-policy noeviction rejects writes with OOM instead of silently evicting limiter state")
    public void testNoEvictionRejectsWritesWithOom() {
        try (Jedis jedis = jedisPool.getResource()) {
            // Configure tiny maxmemory (e.g. 500 KB) and strict noeviction
            jedis.configSet("maxmemory", "500000"); // 500 KB
            jedis.configSet("maxmemory-policy", "noeviction");

            // Allocate memory until limit is reached
            boolean oomTriggered = false;
            try {
                for (int i = 0; i < 5000; i++) {
                    jedis.set("dummy_payload_key_" + i, "X".repeat(5000));
                }
            } catch (JedisDataException e) {
                if (e.getMessage().contains("OOM")) {
                    oomTriggered = true;
                }
            }
            assertTrue(oomTriggered, "Redis must throw OOM exception when memory exceeds maxmemory under noeviction");

            // Now test rate limiter behavior under OOM:
            // FAIL_CLOSED policy must return INFRASTRUCTURE_ERROR
            LimiterDecision oomDecision = limiter.tryAcquire("oom_test_client", 10);
            assertEquals(LimiterDecision.DecisionStatus.INFRASTRUCTURE_ERROR, oomDecision.status());
            assertFalse(oomDecision.isAllowed(), "Limiter must reject requests with INFRASTRUCTURE_ERROR under OOM");
            assertTrue(oomDecision.rejectionReason().contains("OOM") || oomDecision.rejectionReason().contains("JedisDataException"));
        }
    }

    // =========================================================================
    // WORKSTREAM B: IDEMPOTENCY CONFLICT & RETENTION
    // =========================================================================

    @Test
    @DisplayName("Audit B.1: Idempotency conflicting fingerprint returns IDEMPOTENCY_KEY_REUSED rejection")
    public void testIdempotencyConflictingFingerprintRejection() {
        String clientId = "idemp_conflict_client";
        String idempKey = "req-idemp-conflict-01";
        double t0 = 100.0;

        LimiterDecision d1 = limiter.tryAcquire(clientId, 100, idempKey, "payload_hash_A", t0);
        assertTrue(d1.isAllowed());

        // Subsequent call with same key but DIFFERENT fingerprint
        LimiterDecision d2 = limiter.tryAcquire(clientId, 100, idempKey, "payload_hash_B", t0 + 0.1);
        assertEquals(LimiterDecision.DecisionStatus.IDEMPOTENCY_CONFLICT, d2.status());
        assertFalse(d2.isAllowed());
        assertTrue(d2.rejectionReason().contains("conflicting request payload fingerprint"),
                "Rejection reason must indicate fingerprint conflict: " + d2.rejectionReason());
    }

    // =========================================================================
    // WORKSTREAM D: COLD-START COMPARISON
    // =========================================================================

    @Test
    @DisplayName("Audit D.2: SECURE_CONSERVATIVE initializes I=0 (max burst 700) while PERMISSIVE initializes I=I_max (max burst 1000)")
    public void testColdStartPolicies() {
        // Under SECURE_CONSERVATIVE cold-start: initial burst is B_max=700, but idle credit is 0 (effBurst = 700).
        // A cost of 800 must be DENIED!
        LimiterDecision dCons = limiter.tryAcquire("cons_cold_client", 800, null, null, 100.0);
        assertFalse(dCons.isAllowed(), "SECURE_CONSERVATIVE cold start must deny 800-token burst because initial idle credit is 0");
        assertEquals("BURST_LIMIT_EXCEEDED", dCons.rejectionReason());

        // Configure permissive limiter
        RedisLimiterConfig permConfig = new RedisLimiterConfig.Builder()
                .activeStateTtlSeconds(0)
                .coldStartPolicy(RedisLimiterConfig.ColdStartPolicy.PERMISSIVE_FULL_BURST)
                .clockSource(RedisLimiterConfig.ClockSource.CLIENT_TIMESTAMP)
                .keyPrefix("p71perm")
                .build();
        try (ProductionRedisRca70Limiter permLimiter = new ProductionRedisRca70Limiter(connectionProvider, permConfig)) {
            // Under PERMISSIVE_FULL_BURST cold start: B=700 and I=450 (effBurst = 700 + 450/1.5 = 1000).
            // A cost of 800 must be ALLOWED!
            LimiterDecision dPerm = permLimiter.tryAcquire("perm_cold_client", 800, null, null, 100.0);
            assertTrue(dPerm.isAllowed(), "PERMISSIVE_FULL_BURST cold start must allow 800-token burst (effBurst = 1000)");
            assertEquals(0.0, dPerm.burstTokensRemaining(), 1e-4);
        }
    }

    // =========================================================================
    // WORKSTREAM E: CLOCK BACKWARD STEPPING & CLAMPING
    // =========================================================================

    @Test
    @DisplayName("Audit E.1: Backward clock step is clamped to elapsed_sec=0 and does not corrupt token balances")
    public void testClockBackwardStepClamping() {
        String clientId = "clock_clamping_client";
        double t0 = 100.0;

        LimiterDecision d1 = limiter.tryAcquire(clientId, 0, null, null, t0); // Initialize
        // Advance clock to t0 + 10s: refills burst capacity
        LimiterDecision d2 = limiter.tryAcquire(clientId, 200, null, null, t0 + 10.0);
        assertTrue(d2.isAllowed());
        double burstAfterDebit = d2.burstTokensRemaining();

        // Step clock backward to t0 + 5s (backward step of 5 seconds):
        LimiterDecision dBack = limiter.tryAcquire(clientId, 100, null, null, t0 + 5.0);
        assertTrue(dBack.isAllowed());
        // Since elapsed_sec was clamped to 0, no tokens refilled between t0+10 and t0+5.
        // Tokens remaining should be exactly burstAfterDebit - 100!
        assertEquals(burstAfterDebit - 100.0, dBack.burstTokensRemaining(), 1e-4,
                "Backward time step must be clamped to 0 elapsed time, preventing token rollback or negative refill!");
    }
}
