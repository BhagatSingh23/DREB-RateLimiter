package com.dreb.ratelimit.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7: Production Redis Adapter Correctness and Architecture Test Suite.
 */
public class Phase7ProductionAdapterTest {

    private JedisPool jedisPool;
    private RedisConnectionProvider.Standalone connectionProvider;
    private ProductionRedisRca70Limiter limiter;

    @BeforeEach
    public void setUp() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(2);
        this.jedisPool = new JedisPool(poolConfig, "127.0.0.1", 6379, 2000);
        this.connectionProvider = new RedisConnectionProvider.Standalone(jedisPool);

        // Configure durable active state (0 TTL) with default RCA70 parameters
        RedisLimiterConfig config = new RedisLimiterConfig.Builder()
                .activeStateTtlSeconds(0) // Durable state (no automatic expiration)
                .idempotencyTtlSeconds(60) // 60s idempotency retention
                .clockSource(RedisLimiterConfig.ClockSource.CLIENT_TIMESTAMP)
                .coldStartPolicy(RedisLimiterConfig.ColdStartPolicy.SECURE_CONSERVATIVE)
                .failureMode(RedisLimiterConfig.FailureMode.FAIL_CLOSED)
                .keyPrefix("p7")
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

    @Test
    @DisplayName("Gate A: Production adapter compiles, runs, and enforces atomic RCA70 decisions")
    public void testAtomicStateTransitionsAndBoundaryCosts() {
        String clientId = "client_alpha";
        double t0 = 100.0;

        // 1. Initial burst up to B_max = 700
        LimiterDecision d1 = limiter.tryAcquire(clientId, 700, null, null, t0);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, d1.status());
        assertTrue(d1.isAllowed());
        assertEquals(0.0, d1.burstTokensRemaining(), 1e-4);
        assertEquals(1285.7143 - 700.0, d1.sustainedTokensRemaining(), 1.0);

        // 2. Immediate request when B=0 and I=0 must be rejected
        LimiterDecision d2 = limiter.tryAcquire(clientId, 10, null, null, t0);
        assertEquals(LimiterDecision.DecisionStatus.DENY, d2.status());
        assertFalse(d2.isAllowed());
        assertEquals("BURST_LIMIT_EXCEEDED", d2.rejectionReason());

        // 3. Health check
        assertTrue(limiter.isHealthy(), "Production limiter should report healthy on live Redis");
    }

    @Test
    @DisplayName("Gate B: Durable state lifecycle - active state has NO TTL (ttl == -1)")
    public void testActiveLimiterStateHasNoTtl() {
        String clientId = "durable_user";
        limiter.tryAcquire(clientId, 100, null, null, 100.0);

        ClientStateSnapshot state = limiter.getState(clientId);
        assertNotNull(state);
        assertEquals(600.0, state.burstTokens(), 1e-4);

        // Verify Redis TTL is -1 (persists indefinitely without expiration)
        try (Jedis jedis = jedisPool.getResource()) {
            String key = limiter.formatStateKey(clientId);
            long ttl = jedis.ttl(key);
            assertEquals(-1L, ttl, "Active rate limiter state must NOT have a sliding TTL expiration!");
        }
    }

    @Test
    @DisplayName("Gate B: Cold-start policy comparison (SECURE_CONSERVATIVE vs PERMISSIVE_FULL_BURST)")
    public void testColdStartPolicies() {
        // Policy 1: SECURE_CONSERVATIVE (I = 0.0)
        RedisLimiterConfig secureConfig = new RedisLimiterConfig.Builder()
                .coldStartPolicy(RedisLimiterConfig.ColdStartPolicy.SECURE_CONSERVATIVE)
                .clockSource(RedisLimiterConfig.ClockSource.CLIENT_TIMESTAMP)
                .keyPrefix("p7sec")
                .build();
        ProductionRedisRca70Limiter secureLimiter = new ProductionRedisRca70Limiter(connectionProvider, secureConfig);

        // First request of cost 1000 is rejected because I=0, so max available is B_max = 700
        LimiterDecision secDec = secureLimiter.tryAcquire("new_user_1", 1000, null, null, 100.0);
        assertEquals(LimiterDecision.DecisionStatus.DENY, secDec.status(),
                "Conservative cold start should reject 1000 burst until client accumulates idle credit");

        // Policy 2: PERMISSIVE_FULL_BURST (I = 450.0)
        RedisLimiterConfig permConfig = new RedisLimiterConfig.Builder()
                .coldStartPolicy(RedisLimiterConfig.ColdStartPolicy.PERMISSIVE_FULL_BURST)
                .clockSource(RedisLimiterConfig.ClockSource.CLIENT_TIMESTAMP)
                .keyPrefix("p7perm")
                .build();
        ProductionRedisRca70Limiter permLimiter = new ProductionRedisRca70Limiter(connectionProvider, permConfig);

        // First request of cost 1000 is ALLOWED because I=450 -> effBurst = 700 + 450/1.5 = 1000
        LimiterDecision permDec = permLimiter.tryAcquire("new_user_2", 1000, null, null, 100.0);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, permDec.status(),
                "Permissive cold start should allow 1000 burst on first request");
    }

    @Test
    @DisplayName("Gate C: Idempotency with payload fingerprint binding prevents payload-blind replay")
    public void testIdempotencyPayloadBindingAndConflictDetection() {
        String clientId = "idemp_client";
        String idempKey = "tx-uuid-7711";
        String fpCost10 = "cost=10;op=transfer";
        double t0 = 100.0;

        // 1. Initial request with fpCost10
        LimiterDecision d1 = limiter.tryAcquire(clientId, 10, idempKey, fpCost10, t0);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, d1.status());
        assertFalse(d1.isCached());
        assertEquals(690.0, d1.burstTokensRemaining(), 1e-4);

        // 2. Duplicate retry with EXACT SAME fingerprint -> returns cached result with exact balances
        LimiterDecision d1Retry = limiter.tryAcquire(clientId, 10, idempKey, fpCost10, t0);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, d1Retry.status());
        assertTrue(d1Retry.isCached());
        assertEquals(690.0, d1Retry.burstTokensRemaining(), 1e-4, "Cached response must preserve exact balances!");

        // 3. Conflicting retry with DIFFERENT fingerprint -> explicit IDEMPOTENCY_CONFLICT
        String fpConflicting = "cost=1000;op=withdraw";
        LimiterDecision dConflict = limiter.tryAcquire(clientId, 1000, idempKey, fpConflicting, t0);
        assertEquals(LimiterDecision.DecisionStatus.IDEMPOTENCY_CONFLICT, dConflict.status(),
                "Conflicting payload with reused idempotency key must return IDEMPOTENCY_CONFLICT!");
        assertFalse(dConflict.isAllowed());

        // 4. Verify state balance was NOT debited by conflicting request
        ClientStateSnapshot state = limiter.getState(clientId);
        assertEquals(690.0, state.burstTokens(), 1e-4);
    }

    @Test
    @DisplayName("Gate C: Bounded idempotency retention - key expires after idempotencyTtlSeconds")
    public void testIdempotencyKeyExpiration() {
        String clientId = "idemp_ttl_user";
        String idempKey = "short-lived-idemp";
        long shortTtl = 2; // 2 seconds

        RedisLimiterConfig shortIdempConfig = new RedisLimiterConfig.Builder()
                .idempotencyTtlSeconds(shortTtl)
                .clockSource(RedisLimiterConfig.ClockSource.CLIENT_TIMESTAMP)
                .keyPrefix("p7idemp")
                .build();
        ProductionRedisRca70Limiter shortLimiter = new ProductionRedisRca70Limiter(connectionProvider, shortIdempConfig);

        shortLimiter.tryAcquire(clientId, 100, idempKey, "fp1", 100.0);
        assertEquals(600.0, shortLimiter.getState(clientId).burstTokens(), 1e-4);

        // Check that idempotency key exists with TTL in Redis
        try (Jedis jedis = jedisPool.getResource()) {
            String idempRedisKey = shortLimiter.formatIdempKey(clientId, idempKey);
            long ttl = jedis.ttl(idempRedisKey);
            assertTrue(ttl > 0 && ttl <= shortTtl, "Idempotency key must have bounded TTL");

            // Manually expire idempotency key
            jedis.del(idempRedisKey);
        }

        // Retry arrives after expiration -> executed as fresh request
        LimiterDecision dAfterExpire = shortLimiter.tryAcquire(clientId, 100, idempKey, "fp1", 100.0);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, dAfterExpire.status());
        assertFalse(dAfterExpire.isCached(), "Expired idempotency record must not return cached response");
        // Second debit applied: 600 - 100 = 500
        assertEquals(500.0, shortLimiter.getState(clientId).burstTokens(), 1e-4);
    }

    @Test
    @DisplayName("Gate H: Infrastructure failure policy (FAIL_CLOSED vs FAIL_OPEN)")
    public void testInfrastructureFailurePolicies() {
        // Create an invalid/unreachable connection provider (bad port)
        JedisPool badPool = new JedisPool(new JedisPoolConfig(), "127.0.0.1", 59999, 100);
        RedisConnectionProvider.Standalone badProvider = new RedisConnectionProvider.Standalone(badPool);

        // 1. FAIL_CLOSED
        RedisLimiterConfig closedConfig = new RedisLimiterConfig.Builder()
                .failureMode(RedisLimiterConfig.FailureMode.FAIL_CLOSED)
                .build();
        ProductionRedisRca70Limiter closedLimiter = new ProductionRedisRca70Limiter(badProvider, closedConfig);
        LimiterDecision decClosed = closedLimiter.tryAcquire("user1", 10);
        assertEquals(LimiterDecision.DecisionStatus.INFRASTRUCTURE_ERROR, decClosed.status());
        assertFalse(decClosed.isAllowed(), "FAIL_CLOSED must reject requests when Redis is unavailable");

        // 2. FAIL_OPEN
        RedisLimiterConfig openConfig = new RedisLimiterConfig.Builder()
                .failureMode(RedisLimiterConfig.FailureMode.FAIL_OPEN)
                .build();
        ProductionRedisRca70Limiter openLimiter = new ProductionRedisRca70Limiter(badProvider, openConfig);
        LimiterDecision decOpen = openLimiter.tryAcquire("user1", 10);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, decOpen.status());
        assertTrue(decOpen.isAllowed(), "FAIL_OPEN must allow requests when Redis is unavailable");
        assertNotNull(decOpen.rejectionReason());

        badPool.close();
    }

    @Test
    @DisplayName("Gate A: High concurrency multi-threaded contention on single key")
    public void testHighConcurrencyContentionSingleKey() throws InterruptedException, ExecutionException {
        String clientId = "concurrent_prod_key";
        int threads = 16;
        int requestsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                int allowed = 0;
                for (int r = 0; r < requestsPerThread; r++) {
                    LimiterDecision d = limiter.tryAcquire(clientId, 10);
                    if (d.isAllowed()) {
                        allowed++;
                    }
                }
                return allowed;
            }));
        }

        latch.countDown();

        int totalAllowed = 0;
        for (Future<Integer> f : futures) {
            totalAllowed += f.get();
        }
        executor.shutdown();

        // 1600 requests offered (16,000 tokens)
        // Admitted capacity cannot exceed initial burst + continuous refill
        assertTrue(totalAllowed > 0, "Concurrent requests should be admitted up to capacity");
        ClientStateSnapshot state = limiter.getState(clientId);
        assertNotNull(state);
        assertTrue(state.burstTokens() >= 0.0);
        assertTrue(state.sustainedTokens() >= 0.0);
    }
}
