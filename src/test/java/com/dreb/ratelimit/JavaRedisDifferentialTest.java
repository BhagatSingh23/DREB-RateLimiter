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

@DisplayName("Differential Equivalence Tests: In-Memory Java vs. Redis Lua")
public class JavaRedisDifferentialTest {

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

    @FunctionalInterface
    interface LimiterFactory {
        AbstractRateLimiter create(StateStore store, Clock clock);
    }

    record ConfigCase(String name, LimiterFactory factory) {}

    record StepRecord(int step, double timestamp, double dt, int cost, Decision javaDecision, Decision redisDecision) {}

    @Test
    @DisplayName("Run 1,200 random traces across v1 proportional, v1 burst-matched, and all v2 toggle combinations")
    void testDifferentialEquivalenceOverRandomTraces() {
        assertNotNull(jedisPool, "Redis server must be running (local Redis 7 or Testcontainers)");
        RedisHashClient redisClient = new JedisHashClient(jedisPool);

        List<ConfigCase> configs = new ArrayList<>();

        // 1. v1 proportional (legacyOverdraw false and true)
        configs.add(new ConfigCase("v1_proportional", (store, clock) ->
                new DREBLimiter(store, clock,
                        new ConstantRateRefillStrategy(50.0, 5.0, 100.0, 10.0),
                        new LinearIdleCreditPolicy(5.0, 50.0, 1.5),
                        100.0, false)));
        configs.add(new ConfigCase("v1_proportional_legacy", (store, clock) ->
                new DREBLimiter(store, clock,
                        new ConstantRateRefillStrategy(50.0, 5.0, 100.0, 10.0),
                        new LinearIdleCreditPolicy(5.0, 50.0, 1.5),
                        100.0, true)));

        // 2. v1 burst-matched (legacyOverdraw false and true)
        configs.add(new ConfigCase("v1_burst_matched", (store, clock) ->
                new DREBLimiter(store, clock,
                        new ConstantRateRefillStrategy(100.0 / 3.0, 5.0, 100.0, 10.0),
                        new LinearIdleCreditPolicy(5.0, 100.0, 1.5),
                        100.0, false)));
        configs.add(new ConfigCase("v1_burst_matched_legacy", (store, clock) ->
                new DREBLimiter(store, clock,
                        new ConstantRateRefillStrategy(100.0 / 3.0, 5.0, 100.0, 10.0),
                        new LinearIdleCreditPolicy(5.0, 100.0, 1.5),
                        100.0, true)));

        // 3. v2 all 8 toggle combinations
        boolean[] bools = {false, true};
        for (boolean decayScaling : bools) {
            for (boolean sustainedCostScaling : bools) {
                for (boolean legacyOverdraw : bools) {
                    String name = String.format("v2_decay%b_cost%b_legacy%b",
                            decayScaling, sustainedCostScaling, legacyOverdraw);
                    configs.add(new ConfigCase(name, (store, clock) ->
                            new DREBLimiterV2(store, clock,
                                    new ConstantRateRefillStrategy(40.0, 8.0, 100.0, 15.0),
                                    new AdaptiveIdleCreditPolicy(6.0, 60.0, 1.2, 0.4, 0.25, 0.05, decayScaling),
                                    100.0, 0.5, sustainedCostScaling, legacyOverdraw)));
                }
            }
        }

        // Test parameters
        int tracesPerConfig = 100; // 12 configs * 100 = 1,200 total traces (>= 1000)
        int stepsPerTrace = 25;
        int[] allowedCosts = {1, 5, 20, 60};
        double[] interArrivalGaps = {
                0.0, 0.0, // simultaneous
                1e-6, 1e-4, 0.001, 0.01, 0.05, // tiny gaps
                0.1, 0.25, 0.5, 1.0, 2.0, // normal gaps
                10.0, 15.0, 25.0, // gaps longer than fill time (fill time = 5-10s)
                100.0, 1000.0, // long idles
                -0.001, -0.5, -2.0, -10.0 // clock rollbacks
        };

        Random rng = new Random(1337);
        int totalTracesRun = 0;
        String runId = UUID.randomUUID().toString();

        for (ConfigCase config : configs) {
            for (int t = 0; t < tracesPerConfig; t++) {
                totalTracesRun++;
                String clientId = "client-" + totalTracesRun;
                String redisPrefix = "diff-" + runId + "-" + totalTracesRun;

                double initialTs = 1761000000.0 + rng.nextDouble() * 50000.0;
                double currentTs = initialTs;

                ManualClock javaClock = new ManualClock(initialTs);
                ManualClock redisClock = new ManualClock(initialTs);

                InMemoryStateStore javaStore = new InMemoryStateStore();
                RedisStateStore redisStore = new RedisStateStore(redisClient, redisPrefix, Duration.ofSeconds(60));

                AbstractRateLimiter javaLimiter = config.factory().create(javaStore, javaClock);
                AbstractRateLimiter redisLimiter = config.factory().create(redisStore, redisClock);

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
                        sb.append(String.format("DIVERGENCE at step %d for config %s (cost=%d, dt=%.6f, t=%.6f)\n",
                                s, config.name(), cost, dt, currentTs));
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

                String stateContext = String.format("Config: %s, Trace: %d, Final State Mismatch",
                        config.name(), t);

                assertEquals(javaState.burstTokens, redisState.burstTokens, 1e-9,
                        stateContext + " [burstTokens]");
                assertEquals(javaState.sustainedTokens, redisState.sustainedTokens, 1e-9,
                        stateContext + " [sustainedTokens]");
                assertEquals(javaState.idleCredit, redisState.idleCredit, 1e-9,
                        stateContext + " [idleCredit]");
                assertEquals(javaState.lastRefillTs, redisState.lastRefillTs, 1e-9,
                        stateContext + " [lastRefillTs]");
                assertEquals(javaState.abuseScore, redisState.abuseScore, 1e-9,
                        stateContext + " [abuseScore]");
            }
        }

        assertEquals(1200, totalTracesRun, "Should have executed exactly 1200 random traces across 12 configurations");
    }
}
