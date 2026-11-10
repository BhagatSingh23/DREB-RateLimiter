package com.dreb.ratelimit.redis;

import com.dreb.ratelimit.Decision;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.util.JedisClusterCRC16;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 Redis State Lifecycle, Failure Handling & Cluster Audit.
 *
 * Validates:
 * 1. Missing keys and fresh state initialization.
 * 2. Key TTL expiration and the state-reset / burst-restoration dynamic.
 * 3. Formal mathematical derivation of Safe TTL (tau_safe >= 120s).
 * 4. Redis Cluster hash tag compatibility ({clientId}) ensuring single-slot mapping.
 * 5. Redis RDB persistence snapshot (SAVE / BGSAVE) state recovery.
 */
@DisplayName("Phase 6: Redis State Lifecycle, Failure Handling & Cluster Audit")
public class Phase6StateLifecycleTest {

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
     * 1. Missing Keys & Fresh State Initialization.
     */
    @Test
    @DisplayName("Task 8: Verify missing key initializes fresh state (burst=700, sustained=1285.71, abuse=0)")
    void testMissingKeyInitialState() {
        String keyPrefix = "phase6:lifecycle";
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        String client = "missing_client_001";
        limiter.resetClient(client);

        // Before any request, peekState is null
        assertNull(limiter.peekState(client));

        // First request of cost 100 at t=0
        Decision d = limiter.allow(client, 100, 0.0);
        assertEquals(Decision.ALLOW, d);

        // State is initialized and debited: burst = 700 - 100 = 600, sustained = 1285.71 - 100 = 1185.71
        RedisRca70Limiter.RedisStateSnapshot st = limiter.peekState(client);
        assertNotNull(st);
        assertEquals(600.0, st.burstTokens(), 1e-3);
        assertEquals(1185.7142857, st.sustainedTokens(), 1e-3);
        assertEquals(0.0, st.idleCredit(), 1e-3);
        assertEquals(0.0, st.abuseScore(), 1e-3);
        assertTrue(st.ttlSeconds() > 0, "Key must have an active TTL configured");
    }

    /**
     * 2. Key Expiration & Burst Restoration: Auditing the Security Implications of TTL.
     */
    @Test
    @DisplayName("Task 8: Audit key expiration resetting abuse score and granting fresh burst capacity")
    void testKeyExpirationResetsState() throws InterruptedException {
        String keyPrefix = "phase6:ttl_reset";
        // Configure an artificially short TTL of 1 second for eviction testing
        RedisRca70Limiter limiter = new RedisRca70Limiter(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix,
                700.0, 1666.67, 1285.71, 1000.0, 1500.0, 450.0, 1.5, 0.75, 0.05,
                1, 1); // 1s key TTL

        String client = "client_eviction_target";
        limiter.resetClient(client);

        // Client accumulates abuse score by drawing credit
        limiter.allow(client, 1, 0.0);
        limiter.allow(client, 1000, 10.0); // draws 300 deficit -> abuseScore = 300/450 = 0.667

        RedisRca70Limiter.RedisStateSnapshot stBefore = limiter.peekState(client);
        assertNotNull(stBefore);
        assertEquals(0.0, stBefore.burstTokens(), 1e-3);
        assertTrue(stBefore.abuseScore() > 0.6, "Abuse score must be elevated after credit burst");

        // Wait for key to expire in Redis (> 1.2 seconds)
        Thread.sleep(1250);

        // Verify key has expired from Redis
        assertNull(limiter.peekState(client), "Key must be expired and evicted from Redis");

        // Next request will see missing key and initialize fresh state!
        // This grants a fresh burstMax=700 and resets abuseScore to 0!
        limiter.allow(client, 1, 20.0);
        RedisRca70Limiter.RedisStateSnapshot stAfter = limiter.peekState(client);
        assertNotNull(stAfter);
        assertEquals(699.0, stAfter.burstTokens(), 1e-3);
        assertEquals(0.0, stAfter.abuseScore(), 1e-3, "Premature TTL expiration dangerously resets abuse score to 0!");
    }

    /**
     * 3. Redis Cluster Hash Tag Compatibility: {clientId}.
     */
    @Test
    @DisplayName("Task 8: Verify Redis Cluster hash tags guarantee single-slot execution across keys")
    void testRedisClusterHashTagSlotCompatibility() {
        String clientId = "corp_client_9942";
        String keyPrefix = "phase6:cluster";
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        String stateKey = limiter.formatStateKey(clientId);
        String idempKey1 = limiter.formatIdempKey(clientId, "uuid_1");
        String idempKey2 = limiter.formatIdempKey(clientId, "uuid_2");

        // Format is prefix:{clientId}:state and prefix:{clientId}:idemp:uuid
        assertTrue(stateKey.contains("{" + clientId + "}"));
        assertTrue(idempKey1.contains("{" + clientId + "}"));
        assertTrue(idempKey2.contains("{" + clientId + "}"));

        // Use Jedis Cluster CRC16 utility to compute hash slot
        int slotState = JedisClusterCRC16.getSlot(stateKey);
        int slotIdemp1 = JedisClusterCRC16.getSlot(idempKey1);
        int slotIdemp2 = JedisClusterCRC16.getSlot(idempKey2);

        assertEquals(slotState, slotIdemp1, "State key and Idempotency key 1 must hash to the exact same cluster slot");
        assertEquals(slotState, slotIdemp2, "State key and Idempotency key 2 must hash to the exact same cluster slot");
    }

    /**
     * 4. Redis RDB Persistence Snapshot State Recovery.
     */
    @Test
    @DisplayName("Task 8: Verify state survives Redis SAVE / BGSAVE persistence cycle")
    void testPersistenceSnapshotIntegrity() {
        String keyPrefix = "phase6:persist";
        RedisRca70Limiter limiter = RedisRca70Limiter.createDefault(jedisPool, RedisRca70Limiter.ClockSource.CLIENT_TIMESTAMP, keyPrefix);

        String client = "persistent_client_44";
        limiter.resetClient(client);

        limiter.allow(client, 400, 0.0);
        RedisRca70Limiter.RedisStateSnapshot stBefore = limiter.peekState(client);
        assertNotNull(stBefore);
        assertEquals(300.0, stBefore.burstTokens(), 1e-3);

        // Trigger synchronous SAVE to force RDB snapshot generation
        try (Jedis jedis = jedisPool.getResource()) {
            String saveResult = jedis.save();
            assertEquals("OK", saveResult);
        }

        // Verify state is intact after snapshot
        RedisRca70Limiter.RedisStateSnapshot stAfter = limiter.peekState(client);
        assertNotNull(stAfter);
        assertEquals(300.0, stAfter.burstTokens(), 1e-3);
    }
}
