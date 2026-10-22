package com.dreb.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TokenBucketLimiter Differential Equivalence and Redis Key Isolation Tests")
public class TokenBucketDifferentialTest {

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
            }
        } catch (Exception ignored) {
        }

        if (!localRedisAvailable) {
            try {
                redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379);
                redisContainer.start();
                redisHost = redisContainer.getHost();
                redisPort = redisContainer.getMappedPort(6379);
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

    record TBConfig(String name, double capacity, double refillRate) {}

    record StepRecord(int step, double timestamp, double dt, int cost, Decision javaDecision, Decision redisDecision) {}

    @Test
    @DisplayName("Run 1,200 random traces across several TokenBucket (capacity, refillRate) pairs including 50000")
    void testTokenBucketDifferentialEquivalence() {
        assertNotNull(jedisPool, "Redis server must be running (local Redis 7 or Testcontainers)");
        RedisHashClient redisClient = new JedisHashClient(jedisPool);

        List<TBConfig> configs = List.of(
                new TBConfig("large_capacity_50k", 50000.0, 5000.0),
                new TBConfig("medium_capacity_500", 500.0, 50.0),
                new TBConfig("standard_capacity_100", 100.0, 10.0),
                new TBConfig("small_capacity_20", 20.0, 5.0),
                new TBConfig("fractional_rate_50", 50.0, 2.5),
                new TBConfig("tight_capacity_10", 10.0, 1.0)
        );

        int tracesPerConfig = 200; // 6 configs * 200 = 1,200 traces (>= 1,000)
        int stepsPerTrace = 25;
        int[] allowedCosts = {1, 5, 20, 60};

        Random rng = new Random(42);
        int totalTracesRun = 0;
        String runId = UUID.randomUUID().toString();

        for (TBConfig config : configs) {
            double fillTime = config.capacity() / config.refillRate();
            double[] interArrivalGaps = {
                    0.0, 0.0,                     // simultaneous
                    1e-6, 1e-4, 0.001, 0.01, 0.05, // tiny
                    0.1, 0.25, 0.5, 1.0, 2.0,      // normal
                    fillTime * 1.2, fillTime * 2.5, // longer than fill time
                    100.0, 1000.0,                // long idles
                    -0.001, -0.5, -2.0, -10.0      // clock rollbacks
            };

            for (int t = 0; t < tracesPerConfig; t++) {
                totalTracesRun++;
                String clientId = "tb-client-" + totalTracesRun;
                String redisPrefix = "tb-diff-" + runId + "-" + totalTracesRun;

                double initialTs = 1761000000.0 + rng.nextDouble() * 50000.0;
                double currentTs = initialTs;

                ManualClock javaClock = new ManualClock(initialTs);
                ManualClock redisClock = new ManualClock(initialTs);

                InMemoryStateStore javaStore = new InMemoryStateStore();
                RedisStateStore redisStore = new RedisStateStore(redisClient, redisPrefix, Duration.ofSeconds(60));

                TokenBucketLimiter javaLimiter = new TokenBucketLimiter(javaStore, javaClock, config.capacity(), config.refillRate());
                TokenBucketLimiter redisLimiter = new TokenBucketLimiter(redisStore, redisClock, config.capacity(), config.refillRate());

                List<StepRecord> history = new ArrayList<>();

                for (int s = 0; s < stepsPerTrace; s++) {
                    int cost = allowedCosts[rng.nextInt(allowedCosts.length)];
                    double dt = interArrivalGaps[rng.nextInt(interArrivalGaps.length)];
                    currentTs += dt;

                    javaClock.set(currentTs);
                    redisClock.set(currentTs);

                    Decision javaDecision = javaLimiter.allow(clientId, cost);
                    Decision redisDecision = redisLimiter.allow(clientId, cost);

                    history.add(new StepRecord(s, currentTs, dt, cost, javaDecision, redisDecision));

                    if (javaDecision != redisDecision) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(String.format("DIVERGENCE at step %d for config %s (capacity=%.1f, rate=%.1f, cost=%d, dt=%.6f, t=%.6f)\n",
                                s, config.name(), config.capacity(), config.refillRate(), cost, dt, currentTs));
                        sb.append(String.format("Expected Java decision: %s, Actual Redis decision: %s\n",
                                javaDecision, redisDecision));
                        sb.append("Trace history:\n");
                        for (StepRecord rec : history) {
                            sb.append(String.format("  Step %d: t=%.6f, dt=%.6f, cost=%d => Java: %s, Redis: %s\n",
                                    rec.step(), rec.timestamp(), rec.dt(), rec.cost(), rec.javaDecision(), rec.redisDecision()));
                        }
                        assertEquals(javaDecision, redisDecision, sb.toString());
                    }
                }

                // Assert final state fields match within 1e-9
                ClientState javaState = javaLimiter.peekState(clientId);
                ClientState redisState = redisLimiter.peekState(clientId);

                String stateContext = String.format("Config: %s (cap=%.1f, rate=%.1f), Trace: %d, Final State Mismatch\n"
                                + "Java State:  %s\n"
                                + "Redis State: %s\n",
                        config.name(), config.capacity(), config.refillRate(), t, javaState, redisState);

                assertEquals(javaState.burstTokens, redisState.burstTokens, 1e-9,
                        stateContext + " [burstTokens mismatch]");
                assertEquals(javaState.sustainedTokens, redisState.sustainedTokens, 1e-9,
                        stateContext + " [sustainedTokens mismatch]");
                assertEquals(javaState.idleCredit, redisState.idleCredit, 1e-9,
                        stateContext + " [idleCredit mismatch]");
                assertEquals(javaState.lastRefillTs, redisState.lastRefillTs, 1e-9,
                        stateContext + " [lastRefillTs mismatch]");
                assertEquals(javaState.abuseScore, redisState.abuseScore, 1e-9,
                        stateContext + " [abuseScore mismatch]");
            }
        }

        assertEquals(1200, totalTracesRun, "Should have executed exactly 1200 random traces across 6 TokenBucket configurations");
    }

    @Test
    @DisplayName("Redis atomic path: DREB v1 and v2 on different keys never interfere, and TokenBucket prefix does not collide with DREB prefix")
    void testRedisKeyAndPrefixIsolation() {
        assertNotNull(jedisPool, "Redis server must be running");
        RedisHashClient redisClient = new JedisHashClient(jedisPool);

        String runId = UUID.randomUUID().toString();
        String drebPrefix = "isolation-dreb-" + runId;
        String tbPrefix = "isolation-tb-" + runId;

        ManualClock clock = new ManualClock(1000.0);

        RedisStateStore drebStore = new RedisStateStore(redisClient, drebPrefix, Duration.ofSeconds(60));
        RedisStateStore tbStore = new RedisStateStore(redisClient, tbPrefix, Duration.ofSeconds(60));

        // DREB v1 limiter on drebPrefix
        DREBLimiter v1Limiter = new DREBLimiter(drebStore, clock,
                new ConstantRateRefillStrategy(20.0, 5.0, 50.0, 10.0),
                new LinearIdleCreditPolicy(5.0, 30.0, 1.5),
                50.0);

        // DREB v2 limiter on same drebStore (same prefix, different client keys)
        DREBLimiterV2 v2Limiter = new DREBLimiterV2(drebStore, clock,
                new ConstantRateRefillStrategy(20.0, 5.0, 50.0, 10.0),
                new AdaptiveIdleCreditPolicy(5.0, 30.0, 1.0, 0.5, 0.2, 0.1, true),
                50.0, 0.5, true, false);

        // TokenBucketLimiter on tbStore (different prefix, testing shared client key)
        TokenBucketLimiter tbLimiter = new TokenBucketLimiter(tbStore, clock, 20.0, 5.0);

        String v1Client = "client-v1";
        String v2Client = "client-v2";
        String sharedClient = "client-shared"; // Used in both tbStore and drebStore to verify prefix isolation

        // 1. Drain v1Client completely on DREB v1
        for (int i = 0; i < 20; i++) {
            assertEquals(Decision.ALLOW, v1Limiter.allow(v1Client, 1));
        }
        assertEquals(Decision.DENY, v1Limiter.allow(v1Client, 1), "v1Client burst should be exhausted");

        // 2. Verify v2Client on DREB v2 is completely untouched (full capacity)
        ClientState v2Initial = v2Limiter.peekState(v2Client);
        assertEquals(20.0, v2Initial.burstTokens, 1e-9, "v2Client burstTokens must not be affected by v1Client drain");
        assertEquals(50.0, v2Initial.sustainedTokens, 1e-9, "v2Client sustainedTokens must not be affected by v1Client drain");
        assertEquals(0.0, v2Initial.abuseScore, 1e-9);

        // v2Client allows requests normally
        assertEquals(Decision.ALLOW, v2Limiter.allow(v2Client, 15));
        ClientState v2After = v2Limiter.peekState(v2Client);
        assertEquals(5.0, v2After.burstTokens, 1e-9);

        // v1Client is still denied
        assertEquals(Decision.DENY, v1Limiter.allow(v1Client, 1));

        // 3. Test Prefix Isolation: sharedClient on TokenBucketLimiter vs DREB v1Limiter
        // Drain sharedClient completely on TokenBucketLimiter (tbPrefix)
        for (int i = 0; i < 20; i++) {
            assertEquals(Decision.ALLOW, tbLimiter.allow(sharedClient, 1));
        }
        assertEquals(Decision.DENY, tbLimiter.allow(sharedClient, 1), "sharedClient on TB should be exhausted");

        // Now access sharedClient on DREB v1Limiter (drebPrefix) - must be full capacity!
        ClientState drebSharedState = v1Limiter.peekState(sharedClient);
        assertEquals(20.0, drebSharedState.burstTokens, 1e-9,
                "sharedClient on drebPrefix must have full burst capacity despite tbPrefix drain");
        assertEquals(50.0, drebSharedState.sustainedTokens, 1e-9,
                "sharedClient on drebPrefix must have full sustained capacity despite tbPrefix drain");

        // DREB v1 allows full burst on sharedClient
        assertEquals(Decision.ALLOW, v1Limiter.allow(sharedClient, 20));

        // While TB sharedClient remains denied
        assertEquals(Decision.DENY, tbLimiter.allow(sharedClient, 1));
    }
}
