package com.dreb.ratelimit.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.exceptions.JedisAskDataException;
import redis.clients.jedis.exceptions.JedisClusterOperationException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisMovedDataException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7.1 Workstream C Audit:
 * Advanced Live Cluster Behavior, Redirections (MOVED/ASK), Failover, and NOSCRIPT Recovery.
 */
public class Phase71ClusterAdvancedAuditTest {

    private JedisCluster jedisCluster;
    private RedisConnectionProvider.Cluster connectionProvider;
    private ProductionRedisRca70Limiter clusterLimiter;

    @BeforeEach
    public void setUp() {
        Set<HostAndPort> nodes = Set.of(
                new HostAndPort("127.0.0.1", 7001),
                new HostAndPort("127.0.0.1", 7002),
                new HostAndPort("127.0.0.1", 7003)
        );
        this.jedisCluster = new JedisCluster(nodes, 2000, 5);
        this.connectionProvider = new RedisConnectionProvider.Cluster(jedisCluster);

        RedisLimiterConfig config = new RedisLimiterConfig.Builder()
                .activeStateTtlSeconds(0)
                .idempotencyTtlSeconds(60)
                .keyPrefix("p71c")
                .build();

        this.clusterLimiter = new ProductionRedisRca70Limiter(connectionProvider, config);
    }

    @AfterEach
    public void tearDown() {
        if (clusterLimiter != null) {
            clusterLimiter.close();
        }
    }

    @Test
    @DisplayName("Audit C.1: Direct query to incorrect node triggers MOVED redirection")
    public void testMovedRedirectionDirect() {
        // user123 hashes to slot 13438 on Node 7003. Querying Node 7001 directly must throw MOVED!
        try (Jedis jedis7001 = new Jedis("127.0.0.1", 7001)) {
            String key = "{user123}:dreb:state";
            JedisMovedDataException ex = assertThrows(JedisMovedDataException.class, () -> {
                jedis7001.hgetAll(key);
            });
            assertTrue(ex.getMessage().contains("MOVED"), "Must contain MOVED redirection: " + ex.getMessage());
            assertEquals(13438, ex.getSlot(), "Redirection slot must match CRC16('user123')");
            assertTrue(ex.getTargetNode().getPort() == 7003 || ex.getTargetNode().getPort() == 7005);
        }
    }

    private static class ClusterNodeRecord {
        String id;
        String host;
        int port;
        boolean isMaster;
        String masterId;
        List<int[]> slotRanges = new java.util.ArrayList<>();
    }

    private static List<ClusterNodeRecord> parseClusterNodes(String rawNodes) {
        List<ClusterNodeRecord> list = new java.util.ArrayList<>();
        for (String line : rawNodes.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 8) continue;
            ClusterNodeRecord r = new ClusterNodeRecord();
            r.id = parts[0];
            String[] hostPort = parts[1].split("@")[0].split(":");
            r.host = hostPort[0];
            r.port = Integer.parseInt(hostPort[1]);
            r.isMaster = parts[2].contains("master");
            r.masterId = parts[3];
            for (int i = 8; i < parts.length; i++) {
                String slotPart = parts[i];
                if (slotPart.startsWith("[")) continue; // migrating/importing markers
                if (slotPart.contains("-")) {
                    String[] range = slotPart.split("-");
                    r.slotRanges.add(new int[]{Integer.parseInt(range[0]), Integer.parseInt(range[1])});
                } else {
                    try {
                        int slot = Integer.parseInt(slotPart);
                        r.slotRanges.add(new int[]{slot, slot});
                    } catch (NumberFormatException ignored) {}
                }
            }
            list.add(r);
        }
        return list;
    }

    @Test
    @DisplayName("Audit C.2: Live ASK redirection during active slot migration")
    public void testAskRedirectionDuringSlotMigration() {
        try (Jedis seedJedis = new Jedis("127.0.0.1", 7001)) {
            List<ClusterNodeRecord> nodes = parseClusterNodes(seedJedis.clusterNodes());
            ClusterNodeRecord masterA = nodes.stream()
                    .filter(n -> n.isMaster && !n.slotRanges.isEmpty())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No master with slots found"));
            ClusterNodeRecord masterB = nodes.stream()
                    .filter(n -> n.isMaster && !n.id.equals(masterA.id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No second master found"));

            int targetSlot = masterA.slotRanges.get(0)[0];

            try (Jedis jedisA = new Jedis(masterA.host, masterA.port);
                 Jedis jedisB = new Jedis(masterB.host, masterB.port)) {

                jedisA.clusterSetSlotMigrating(targetSlot, masterB.id);
                jedisB.clusterSetSlotImporting(targetSlot, masterA.id);

                try {
                    // Find a key that hashes to targetSlot
                    String testKey = "test_key_" + targetSlot;
                    int counter = 0;
                    while (redis.clients.jedis.util.JedisClusterCRC16.getSlot(testKey) != targetSlot) {
                        testKey = "test_key_" + targetSlot + "_" + (++counter);
                    }

                    final String lookupKey = testKey;
                    JedisAskDataException askEx = assertThrows(JedisAskDataException.class, () -> {
                        jedisA.get(lookupKey);
                    });
                    assertTrue(askEx.getMessage().contains("ASK"), "Must return ASK redirection: " + askEx.getMessage());
                    assertEquals(targetSlot, askEx.getSlot());
                } finally {
                    jedisA.clusterSetSlotStable(targetSlot);
                    jedisB.clusterSetSlotStable(targetSlot);
                }
            }
        }
    }

    @Test
    @DisplayName("Audit C.3: Failover and transparent recovery via JedisCluster")
    public void testClusterFailoverAndTransparentRecovery() {
        String clientId = "failover_resilience_client";

        // Initial request on active master
        LimiterDecision d1 = clusterLimiter.tryAcquire(clientId, 100);
        assertTrue(d1.isAllowed());

        try (Jedis seedJedis = new Jedis("127.0.0.1", 7001)) {
            List<ClusterNodeRecord> nodes = parseClusterNodes(seedJedis.clusterNodes());
            // Find a connected replica
            ClusterNodeRecord replica = nodes.stream()
                    .filter(n -> !n.isMaster && n.masterId != null && !n.masterId.equals("-"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No replica found for failover"));

            try (Jedis jedisReplica = new Jedis(replica.host, replica.port)) {
                String failoverResp = jedisReplica.clusterFailover();
                assertNotNull(failoverResp);
            }

            // Allow cluster election and gossip propagation (up to 1.5 seconds)
            try {
                Thread.sleep(1200);
            } catch (InterruptedException ignored) {}

            // Subsequent request through JedisCluster MUST automatically adapt to new master and succeed
            LimiterDecision d2 = clusterLimiter.tryAcquire(clientId, 100);
            assertTrue(d2.isAllowed(), "JedisCluster must transparently recover and succeed after failover!");
        }
    }

    @Test
    @DisplayName("Audit C.4: NOSCRIPT recovery on unprepared cluster node")
    public void testNoscriptRecoveryOnClusterNode() {
        String clientId = "noscript_audit_client";

        // Flush scripts on all cluster masters to simulate script eviction / restart
        for (int port : List.of(7001, 7002, 7003)) {
            try (Jedis jedis = new Jedis("127.0.0.1", port)) {
                jedis.scriptFlush();
            }
        }

        // Now invoke tryAcquire: Production adapter must catch NOSCRIPT, reload script via scriptLoad, and succeed!
        LimiterDecision decision = clusterLimiter.tryAcquire(clientId, 50);
        assertTrue(decision.isAllowed(), "Limiter must automatically reload Lua script upon NOSCRIPT error");
        assertEquals(650.0, decision.burstTokensRemaining(), 1e-4);
    }

    @Test
    @DisplayName("Audit C.5: Cross-slot rejection for multi-client keys proves multi-tenant transactions are unsupported")
    public void testCrossSlotRejectionForMultiClientKeys() {
        // Attempting to evaluate two different clients in a single script triggers CROSSSLOT error
        try (Jedis jedis7001 = new Jedis("127.0.0.1", 7001)) {
            String multiKeyScript = "return redis.call('MGET', KEYS[1], KEYS[2])";
            JedisDataException ex = assertThrows(JedisDataException.class, () -> {
                jedis7001.eval(multiKeyScript, 2, "{clientA}:state", "{clientB}:state");
            });
            assertTrue(ex.getMessage().contains("CROSSSLOT"),
                    "Multi-tenant transactions across different hash slots MUST be rejected with CROSSSLOT: " + ex.getMessage());
        }
    }
}
