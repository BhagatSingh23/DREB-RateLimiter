package com.dreb.ratelimit.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.exceptions.JedisClusterOperationException;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7: Live Multi-Node Redis Cluster Validation Suite.
 * Validates real execution on 6-node Redis Cluster running on 127.0.0.1:7001-7006.
 */
public class Phase7RedisClusterLiveTest {

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
                .activeStateTtlSeconds(0) // Durable state
                .idempotencyTtlSeconds(60)
                .keyPrefix("p7cluster")
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
    @DisplayName("Gate D: Verify live 6-node Redis Cluster topology and connectivity")
    public void testLiveClusterTopology() {
        assertTrue(clusterLimiter.isHealthy(), "Cluster limiter must report healthy against live cluster");

        Map<String, ?> clusterNodes = jedisCluster.getClusterNodes();
        assertNotNull(clusterNodes);
        // Cluster has 6 nodes (3 primaries + 3 replicas)
        assertTrue(clusterNodes.size() >= 6, "Expected at least 6 cluster nodes, found: " + clusterNodes.size());
    }

    @Test
    @DisplayName("Gate D: Single client key execution and state persistence on cluster slot")
    public void testSingleClientKeyExecutionOnCluster() {
        String clientId = "cluster_tenant_101";
        LimiterDecision d1 = clusterLimiter.tryAcquire(clientId, 200);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, d1.status());
        assertEquals(500.0, d1.burstTokensRemaining(), 1e-4);

        ClientStateSnapshot state = clusterLimiter.getState(clientId);
        assertNotNull(state);
        assertEquals(500.0, state.burstTokens(), 1e-4);

        // Reset
        clusterLimiter.resetState(clientId);
        assertNull(clusterLimiter.getState(clientId));
    }

    @Test
    @DisplayName("Gate D: Multi-client keys distributed across all 3 primary shards")
    public void testMultiClientKeysAcrossClusterShards() {
        // Submit requests for 30 distinct clients to distribute across all 16,384 slots
        int clientCount = 30;
        for (int i = 0; i < clientCount; i++) {
            String clientId = "client_shard_dist_" + i;
            LimiterDecision decision = clusterLimiter.tryAcquire(clientId, 50);
            assertEquals(LimiterDecision.DecisionStatus.ALLOW, decision.status());
            assertEquals(650.0, decision.burstTokensRemaining(), 1e-4);
        }

        // Verify each client can read its state independently
        for (int i = 0; i < clientCount; i++) {
            String clientId = "client_shard_dist_" + i;
            ClientStateSnapshot s = clusterLimiter.getState(clientId);
            assertNotNull(s);
            assertEquals(650.0, s.burstTokens(), 1e-4);
        }
    }

    @Test
    @DisplayName("Gate D: Hash tag affinity ensures atomic script execution without CROSSSLOT error")
    public void testHashTagAffinityEliminatesCrossSlotErrors() {
        String clientId = "user_affinity_99";
        String idempKey = "req-cluster-idemp-01";
        String fp = "cost=150";

        // State key {user_affinity_99}:state and Idemp key {user_affinity_99}:idemp:req
        // both hash to CRC16("user_affinity_99").
        // This MUST execute atomically in Redis Cluster without CROSSSLOT error!
        LimiterDecision d1 = clusterLimiter.tryAcquire(clientId, 150, idempKey, fp);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, d1.status());
        assertFalse(d1.isCached());
        assertEquals(550.0, d1.burstTokensRemaining(), 1e-4);

        // Retry returns cached decision atomically
        LimiterDecision dRetry = clusterLimiter.tryAcquire(clientId, 150, idempKey, fp);
        assertEquals(LimiterDecision.DecisionStatus.ALLOW, dRetry.status());
        assertTrue(dRetry.isCached());
        assertEquals(550.0, dRetry.burstTokensRemaining(), 1e-4);
    }

    @Test
    @DisplayName("Gate D: Concurrent multi-threaded execution across Redis Cluster shards")
    public void testConcurrentMultiThreadedClusterExecution() throws InterruptedException, ExecutionException {
        int threads = 12;
        int requestsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                latch.await();
                int allowed = 0;
                for (int r = 0; r < requestsPerThread; r++) {
                    String clientId = "threaded_client_" + (threadId % 6);
                    LimiterDecision d = clusterLimiter.tryAcquire(clientId, 10);
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

        assertTrue(totalAllowed > 0, "Concurrent cluster requests should be admitted");
    }
}
