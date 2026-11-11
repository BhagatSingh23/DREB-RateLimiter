package com.dreb.ratelimit.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6.1 Audit: Deployment Scope and Source Placement Audit.
 * Tests standalone topology vs cluster readiness and verifies source-set placement.
 */
public class Phase61DeploymentScopeTest {

    private JedisPool jedisPool;

    @BeforeEach
    public void setUp() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(4);
        this.jedisPool = new JedisPool(config, "127.0.0.1", 6379, 2000);
    }

    @AfterEach
    public void tearDown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    @Test
    @DisplayName("Audit Task 4: Verify actual Redis deployment topology is Standalone (cluster_enabled:0)")
    public void testActualTopologyIsStandalone() {
        try (Jedis jedis = jedisPool.getResource()) {
            String clusterInfo = jedis.info("cluster");
            assertNotNull(clusterInfo);
            assertTrue(clusterInfo.contains("cluster_enabled:0"),
                    "Actual Redis server MUST have cluster_enabled:0 (Standalone mode)");

            String replInfo = jedis.info("replication");
            assertNotNull(replInfo);
            assertTrue(replInfo.contains("role:master"), "Redis instance is a standalone master");
            assertTrue(replInfo.contains("connected_slaves:0"), "Standalone master has 0 connected replicas");
        }
    }

    @Test
    @DisplayName("Audit Task 4: Demonstrate that Standalone Redis ignores cluster slot hashing")
    public void testStandaloneRedisIgnoresClusterSlotRestrictions() {
        try (Jedis jedis = jedisPool.getResource()) {
            // In a real Redis Cluster, passing keys with different hash slots to a Lua script
            // throws a CROSSSLOT error (e.g. "CROSSSLOT Keys in request don't hash to the same slot").
            // In Standalone Redis, this executes without error because slot checking is disabled!
            String luaCrossSlot = "return redis.call('MGET', KEYS[1], KEYS[2])";
            jedis.set("{slotA}:key1", "val1");
            jedis.set("{slotB}:key2", "val2");

            // Deliberately incompatible hash tags
            Object res = jedis.eval(luaCrossSlot, 2, "{slotA}:key1", "{slotB}:key2");
            assertNotNull(res, "Standalone Redis executed cross-slot keys without error!");

            // Cleanup
            jedis.del("{slotA}:key1", "{slotB}:key2");
        }
    }

    @Test
    @DisplayName("Audit Task 5: Verify RedisRca70Limiter is in test-source set (target/test-classes)")
    public void testVerifySourcePlacementIsTestOnly() {
        Class<?> clazz = RedisRca70Limiter.class;
        URL location = clazz.getProtectionDomain().getCodeSource().getLocation();
        assertNotNull(location);

        String path = location.getPath();
        assertTrue(path.contains("test-classes") || path.contains("src/test"),
                "RedisRca70Limiter MUST be located in test classpath, NOT production classes. Path: " + path);
        assertFalse(path.endsWith("/classes/") || path.contains("/target/classes/"),
                "RedisRca70Limiter must NOT be in target/classes/");
    }
}
