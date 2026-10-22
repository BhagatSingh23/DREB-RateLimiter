package com.dreb.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Redis Atomic Lua Script Distributed Concurrency Tests")
public class RedisAtomicLuaConcurrencyTest {

    private static GenericContainer<?> redisContainer;
    private static JedisPool jedisPool;
    private static String redisHost = "localhost";
    private static int redisPort = 6379;

    @BeforeAll
    static void setUp() {
        boolean localRedisAvailable = false;
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            if ("PONG".equalsIgnoreCase(jedis.ping())) {
                localRedisAvailable = true;
                redisHost = "localhost";
                redisPort = 6379;
                System.out.println("Using local Redis server on localhost:6379");
            }
        } catch (Exception ignored) {
        }

        if (!localRedisAvailable) {
            try {
                System.out.println("Local Redis not available; starting Testcontainers Redis container...");
                redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379);
                redisContainer.start();
                redisHost = redisContainer.getHost();
                redisPort = redisContainer.getMappedPort(6379);
                System.out.println("Testcontainers Redis container started at " + redisHost + ":" + redisPort);
            } catch (Exception e) {
                System.err.println("Could not start Testcontainers Redis container: " + e.getMessage());
            }
        }

        if (redisHost != null) {
            jedisPool = new JedisPool(redisHost, redisPort);
        }
    }

    @AfterAll
    static void tearDown() {
        if (jedisPool != null) {
            jedisPool.close();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    static class JedisHashClient implements RedisHashClient {
        private final JedisPool pool;

        JedisHashClient(JedisPool pool) {
            this.pool = pool;
        }

        @Override
        public Map<String, String> hgetAll(String key) {
            try (Jedis jedis = pool.getResource()) {
                return jedis.hgetAll(key);
            }
        }

        @Override
        public void hset(String key, Map<String, String> fields) {
            try (Jedis jedis = pool.getResource()) {
                jedis.hset(key, fields);
            }
        }

        @Override
        public long eval(String script, List<String> keys, List<String> args) {
            try (Jedis jedis = pool.getResource()) {
                Object res = jedis.eval(script, keys, args);
                return ((Number) res).longValue();
            }
        }
    }

    @Test
    @DisplayName("Multiple independent limiter instances hammering one Redis key must never exceed capacity")
    void testMultipleIndependentInstancesHammeringOneKey() throws InterruptedException {
        assertNotNull(jedisPool, "Redis server must be running (either local Redis 7 or Testcontainers)");

        RedisHashClient redisClient = new JedisHashClient(jedisPool);

        // Run 5 trials of multi-instance concurrency
        for (int trial = 0; trial < 5; trial++) {
            String testKey = "client-concurrent-" + trial + "-" + System.currentTimeMillis();

            // S_max = 50, B_max = 20, refillRate = 0
            // Independent rate limiter instances (e.g. 10 independent gateway nodes)
            int numInstances = 10;
            List<RateLimiter> instances = new ArrayList<>();
            ManualClock sharedClock = new ManualClock(0.0);

            for (int i = 0; i < numInstances; i++) {
                RedisStateStore store = new RedisStateStore(redisClient, "test-dreb");
                ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(20, 0, 50, 0);
                LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(0, 0, 1.5);
                instances.add(new DREBLimiter(store, sharedClock, refill, idle, 50));
            }

            // Hammer the same client across all 10 independent instances with 200 total concurrent requests
            int totalRequests = 200;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneGate = new CountDownLatch(totalRequests);
            AtomicInteger allowedCount = new AtomicInteger(0);

            for (int i = 0; i < totalRequests; i++) {
                final int instanceIndex = i % numInstances;
                executor.submit(() -> {
                    try {
                        startGate.await();
                        RateLimiter limiter = instances.get(instanceIndex);
                        if (limiter.allow(testKey, 1) == Decision.ALLOW) {
                            allowedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown();
            doneGate.await();
            executor.shutdown();

            // Total admitted across all independent instances must be EXACTLY burstMax = 20
            assertEquals(20, allowedCount.get(),
                    "Under atomic Redis enforcement, total admitted requests across independent instances must be exactly 20 in trial " + trial);
        }
    }

    @Test
    @DisplayName("Redis key expires when TTL is configured")
    void testKeyExpiresWhenTtlConfigured() throws InterruptedException {
        assertNotNull(jedisPool, "Redis server must be running (either local Redis 7 or Testcontainers)");
        RedisHashClient redisClient = new JedisHashClient(jedisPool);

        // Configure a store with 1 second TTL
        Duration ttl = Duration.ofSeconds(1);
        RedisStateStore store = new RedisStateStore(redisClient, "ttl-test", ttl);
        ManualClock clock = new ManualClock(100.0);
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 5, 20, 10);
        LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 10, 1.5);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 20);

        String clientId = "expiring-client-" + System.currentTimeMillis();
        String fullKey = "ttl-test:" + clientId;

        // Perform write via allow
        assertEquals(Decision.ALLOW, limiter.allow(clientId, 1));

        // Immediately verify key exists in Redis and has a TTL > 0
        try (Jedis jedis = jedisPool.getResource()) {
            assertTrue(jedis.exists(fullKey), "Key should exist immediately after write");
            long keyTtl = jedis.ttl(fullKey);
            assertTrue(keyTtl > 0 && keyTtl <= 1, "Key TTL should be set to 1 second, got: " + keyTtl);
        }

        // Wait for expiration (1.2 seconds)
        Thread.sleep(1200);

        // Verify key is gone in Redis
        try (Jedis jedis = jedisPool.getResource()) {
            assertFalse(jedis.exists(fullKey), "Key should have expired and been removed from Redis");
        }

        // State loaded after expiration should be fresh initial state
        ClientState state = store.load(clientId, () -> ClientState.fresh(10, 20, 200.0));
        assertEquals(10.0, state.burstTokens, 1e-9);
        assertEquals(20.0, state.sustainedTokens, 1e-9);
    }

    @Test
    @DisplayName("Timestamp near 1.76e9 with fractional part preserves exact double precision across Lua and Java round trips")
    void testTimestampPrecisionLosslessRoundTrip() {
        assertNotNull(jedisPool, "Redis server must be running (either local Redis 7 or Testcontainers)");
        RedisHashClient redisClient = new JedisHashClient(jedisPool);
        RedisStateStore store = new RedisStateStore(redisClient, "precision-test");

        // Timestamp near 1.76e9 with high-precision fractional part
        double highPrecisionTs = 1761234567.8912345;
        String clientId = "precision-client-" + System.currentTimeMillis();

        // 1. Test Lua script write/read round trip via atomicAllow
        AtomicAllowContext context = AtomicAllowContext.forDrebV1(
                50.0, 10.0, 100.0, 20.0, 5.0, 50.0, 1.5, false);
        Decision d = store.atomicAllow(clientId, 1, highPrecisionTs, context);
        assertEquals(Decision.ALLOW, d);

        ClientState luaLoaded = store.load(clientId, () -> null);
        assertNotNull(luaLoaded);
        assertEquals(highPrecisionTs, luaLoaded.lastRefillTs, 0.0,
                "Lua string.format('%.17g') must preserve exact IEEE-754 binary64 double timestamp");

        // 2. Test Java save / load round trip
        String clientSaveId = "precision-save-" + System.currentTimeMillis();
        ClientState savedState = new ClientState(
                49.12345678901234,
                99.87654321098765,
                12.34567890123456,
                highPrecisionTs,
                0.1234567890123456);
        store.save(clientSaveId, savedState);

        ClientState javaLoaded = store.load(clientSaveId, () -> null);
        assertNotNull(javaLoaded);
        assertEquals(savedState.burstTokens, javaLoaded.burstTokens, 0.0);
        assertEquals(savedState.sustainedTokens, javaLoaded.sustainedTokens, 0.0);
        assertEquals(savedState.idleCredit, javaLoaded.idleCredit, 0.0);
        assertEquals(savedState.lastRefillTs, javaLoaded.lastRefillTs, 0.0);
        assertEquals(savedState.abuseScore, javaLoaded.abuseScore, 0.0);
    }
}
