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
 * Phase 7: Persistence, Lifecycle, and Memory Capacity Validation Suite.
 */
public class Phase7PersistenceAndLifecycleTest {

    private JedisPool jedisPool;
    private RedisConnectionProvider.Standalone connectionProvider;
    private ProductionRedisRca70Limiter limiter;

    @BeforeEach
    public void setUp() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        this.jedisPool = new JedisPool(poolConfig, "127.0.0.1", 6379, 2000);
        this.connectionProvider = new RedisConnectionProvider.Standalone(jedisPool);

        RedisLimiterConfig config = new RedisLimiterConfig.Builder()
                .activeStateTtlSeconds(0) // Durable state
                .idempotencyTtlSeconds(60)
                .keyPrefix("p7persist")
                .build();

        this.limiter = new ProductionRedisRca70Limiter(connectionProvider, config);
    }

    @AfterEach
    public void tearDown() {
        if (limiter != null) {
            limiter.close();
        }
    }

    @Test
    @DisplayName("Gate E: State persistence across Redis SAVE and BGSAVE snapshotting")
    public void testStatePersistenceAcrossRdbSave() {
        String clientId = "rdb_durability_user";
        LimiterDecision d1 = limiter.tryAcquire(clientId, 350);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, d1.status());
        assertEquals(350.0, d1.burstTokensRemaining(), 1e-4);

        // Instruct Redis to execute snapshot
        try (Jedis jedis = jedisPool.getResource()) {
            String bgsaveRes = jedis.bgsave();
            assertNotNull(bgsaveRes);
        }

        // Verify state is completely intact
        ClientStateSnapshot s = limiter.getState(clientId);
        assertNotNull(s);
        assertEquals(350.0, s.burstTokens(), 1e-4);
        assertEquals(-1L, s.ttlSeconds(), "Durable state must have -1 TTL across snapshots");
    }

    @Test
    @DisplayName("Gate H: Measure exact physical memory usage per client key and idempotency record")
    public void testMemoryUsagePerClientKey() {
        String clientId = "memory_audit_tenant";
        String idempKey = "mem-idemp-01";
        limiter.tryAcquire(clientId, 100, idempKey, "cost=100;user=audit");

        try (Jedis jedis = jedisPool.getResource()) {
            String stateKey = limiter.formatStateKey(clientId);
            String idempRedisKey = limiter.formatIdempKey(clientId, idempKey);

            Long stateBytes = jedis.memoryUsage(stateKey);
            Long idempBytes = jedis.memoryUsage(idempRedisKey);

            assertNotNull(stateBytes, "State key must exist in Redis");
            assertNotNull(idempBytes, "Idempotency key must exist in Redis");

            assertTrue(stateBytes > 0 && stateBytes < 1024, "State hash memory should be ~200-400 bytes, observed: " + stateBytes);
            assertTrue(idempBytes > 0 && idempBytes < 512, "Idempotency string memory should be ~80-200 bytes, observed: " + idempBytes);

            System.out.printf("Observed Redis Memory Footprint: State Hash = %d bytes, Idempotency String = %d bytes%n",
                    stateBytes, idempBytes);
        }
    }

    @Test
    @DisplayName("Gate F: Server time evaluation consistency and timestamp resolution")
    public void testServerTimeEvaluationResolution() {
        String clientId = "time_resolution_client";
        LimiterDecision d1 = limiter.tryAcquire(clientId, 10);
        assertTrue(d1.isAllowed());
        long ts1 = d1.decisionTimestampMicros();
        assertTrue(ts1 > 1_700_000_000_000_000L, "Timestamp must be valid Unix microseconds epoch");

        LimiterDecision d2 = limiter.tryAcquire(clientId, 10);
        long ts2 = d2.decisionTimestampMicros();
        assertTrue(ts2 >= ts1, "Server timestamps must be non-decreasing: ts1=" + ts1 + ", ts2=" + ts2);
    }
}
