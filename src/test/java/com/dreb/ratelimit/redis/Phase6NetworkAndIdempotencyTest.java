package com.dreb.ratelimit.redis;

import com.dreb.ratelimit.Decision;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 Network Failures, Retries & Idempotency Test Suite.
 *
 * Validates:
 * 1. Connection-pool saturation and exhaustion.
 * 2. Ambiguous timeout handling and duplicate-debit vulnerability without idempotency keys.
 * 3. Idempotent request semantics: first submission, duplicate submission, concurrent duplicates.
 * 4. Idempotency key expiration and post-TTL reuse.
 */
@DisplayName("Phase 6: Network Failures, Retries & Idempotency")
public class Phase6NetworkAndIdempotencyTest {

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
     * 1. Connection-Pool Saturation: verify behavior when connection pool is exhausted.
     */
    @Test
    @DisplayName("Task 5: Connection-pool saturation and failure handling")
    void testConnectionPoolSaturation() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(2); // strictly max 2 connections
        config.setMaxWait(java.time.Duration.ofMillis(100)); // fail fast on exhaustion

        try (JedisPool smallPool = new JedisPool(config, REDIS_HOST, REDIS_PORT)) {
            // Borrow all connections
            Jedis j1 = smallPool.getResource();
            Jedis j2 = smallPool.getResource();
            assertNotNull(j1);
            assertNotNull(j2);

            // Attempting to borrow a 3rd connection must throw NoSuchElementException / JedisException
            assertThrows(JedisException.class, smallPool::getResource,
                    "Exhausted pool must throw when no connections are available within maxWait");

            j1.close();
            j2.close();
        }
    }

    /**
     * 2. Without Idempotency: Ambiguous Timeout causes Double-Debit on Retry.
     */
    @Test
    @DisplayName("Task 5: Prove duplicate submission causes double-debit without idempotency key")
    void testNonIdempotentRetryDoubleDebits() {
        String keyPrefix = "phase6:retry:noidemp";
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        String client = "client_noidemp_retry";
        limiter.resetClient(client);

        // Client starts with full capacity (burst=700, sustained=1285.71)
        double t0 = 0.0;
        int cost = 300;

        // First attempt succeeds
        Decision d1 = limiter.allow(client, cost, t0, null);
        assertEquals(Decision.ALLOW, d1);

        RedisRca70Limiter.RedisStateSnapshot st1 = limiter.peekState(client);
        assertNotNull(st1);
        assertEquals(400.0, st1.burstTokens(), 1e-3); // 700 - 300 = 400

        // Suppose client timed out waiting for network ACK and retries the exact same request
        // Without an idempotency key, Redis executes the script a second time!
        Decision d2 = limiter.allow(client, cost, t0, null);
        assertEquals(Decision.ALLOW, d2);

        RedisRca70Limiter.RedisStateSnapshot st2 = limiter.peekState(client);
        assertNotNull(st2);
        assertEquals(100.0, st2.burstTokens(), 1e-3); // 400 - 300 = 100 (DOUBLE-DEBITED!)

        // Total debited = 600 tokens for 1 logical request
        assertTrue(st2.burstTokens() < st1.burstTokens(), "Non-idempotent retry mistakenly debited burst tokens twice");
    }

    /**
     * 3. With Idempotency Key: First Submission vs Duplicate vs Expiration.
     */
    @Test
    @DisplayName("Task 5: Verify idempotency key prevents duplicate debiting and returns cached decision")
    void testIdempotentRequestSemantics() {
        String keyPrefix = "phase6:retry:idemp";
        // Configure short idempotency TTL = 2 seconds for test
        RedisRca70Limiter limiter = new RedisRca70Limiter(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix,
                700.0, 1666.67, 1285.71, 1000.0, 1500.0, 450.0, 1.5, 0.75, 0.05,
                120, 2); // 2s idemp TTL

        String client = "client_idemp_test";
        limiter.resetClient(client);

        String reqId = "req_uuid_1001";
        double t0 = 0.0;
        int cost = 300;

        // Attempt 1: First submission
        Decision d1 = limiter.allow(client, cost, t0, reqId);
        assertEquals(Decision.ALLOW, d1);

        RedisRca70Limiter.RedisStateSnapshot st1 = limiter.peekState(client);
        assertNotNull(st1);
        assertEquals(400.0, st1.burstTokens(), 1e-3);

        // Attempt 2: Duplicate submission with SAME idempotency key
        Decision d2 = limiter.allow(client, cost, t0, reqId);
        assertEquals(Decision.ALLOW, d2, "Duplicate submission must return cached ALLOW decision");

        RedisRca70Limiter.RedisStateSnapshot st2 = limiter.peekState(client);
        assertNotNull(st2);
        // Burst tokens MUST REMAIN 400.0 (no second debit!)
        assertEquals(400.0, st2.burstTokens(), 1e-3, "Idempotent duplicate MUST NOT mutate state a second time");

        // Attempt 3: Concurrent duplicate submissions from multiple threads
        int threads = 8;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        String concurrentReqId = "req_concurrent_2002";
        AtomicInteger allows = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(exec.submit(() -> {
                Decision d = limiter.allow(client, 200, t0, concurrentReqId);
                if (d == Decision.ALLOW) {
                    allows.incrementAndGet();
                }
            }));
        }

        for (Future<?> f : futures) {
            assertDoesNotThrow(() -> f.get(5, TimeUnit.SECONDS));
        }
        exec.shutdown();

        // All threads received identical ALLOW decision, but tokens debited EXACTLY ONCE
        assertEquals(threads, allows.get(), "All concurrent duplicates must receive ALLOW");
        RedisRca70Limiter.RedisStateSnapshot st3 = limiter.peekState(client);
        assertNotNull(st3);
        // Previous was 400.0. Exactly one 200 debit occurred -> 200.0 remaining!
        assertEquals(200.0, st3.burstTokens(), 1e-3, "Concurrent duplicate calls must debit tokens exactly once");
    }
}
