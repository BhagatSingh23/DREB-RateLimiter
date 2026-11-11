package com.dreb.ratelimit.redis.example;

import com.dreb.ratelimit.redis.*;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Set;

/**
 * Integration example demonstrating how an application constructs, configures,
 * and invokes the production DREB-RCA70 Redis adapter.
 */
public final class ProductionLimiterExample {

    public static void main(String[] args) {
        // =====================================================================
        // Example 1: Standalone Redis Deployment with Default RCA70 Parameters
        // =====================================================================
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(16);
        poolConfig.setMinIdle(4);

        JedisPool jedisPool = new JedisPool(poolConfig, "127.0.0.1", 6379, 2000);
        RedisConnectionProvider standaloneProvider = new RedisConnectionProvider.Standalone(jedisPool);

        // Configure RCA70 with durable active state (0 TTL) and fail-closed security
        RedisLimiterConfig standaloneConfig = new RedisLimiterConfig.Builder()
                .activeStateTtlSeconds(0) // Durable state (no premature eviction)
                .idempotencyTtlSeconds(60) // 60s idempotency retention horizon
                .coldStartPolicy(RedisLimiterConfig.ColdStartPolicy.SECURE_CONSERVATIVE)
                .failureMode(RedisLimiterConfig.FailureMode.FAIL_CLOSED)
                .build();

        try (DistributedRateLimiter limiter = new ProductionRedisRca70Limiter(standaloneProvider, standaloneConfig)) {
            String clientId = "customer_acme_corp";
            int requestCost = 10;

            // 1. Basic admission check
            LimiterDecision d1 = limiter.tryAcquire(clientId, requestCost);
            System.out.printf("Decision 1: %s, remaining burst: %.2f%n", d1.status(), d1.burstTokensRemaining());

            // 2. Idempotent check with payload fingerprint
            String idempKey = "req-uuid-9981-abcd";
            String payloadHash = "sha256-hash-of-request-body";
            LimiterDecision d2 = limiter.tryAcquire(clientId, 20, idempKey, payloadHash);
            System.out.printf("Decision 2 (Idempotent): %s, cached: %b%n", d2.status(), d2.isCached());

            // 3. Duplicate submission with SAME fingerprint -> safely returns cached result
            LimiterDecision d2Retry = limiter.tryAcquire(clientId, 20, idempKey, payloadHash);
            System.out.printf("Decision 2 Retry: %s, cached: %b%n", d2Retry.status(), d2Retry.isCached());

            // 4. Conflicting submission with DIFFERENT fingerprint -> explicit IDEMPOTENCY_CONFLICT
            LimiterDecision d2Conflict = limiter.tryAcquire(clientId, 50, idempKey, "different-payload-hash");
            System.out.printf("Decision 2 Conflict: %s, reason: %s%n", d2Conflict.status(), d2Conflict.rejectionReason());
        }

        // =====================================================================
        // Example 2: Multi-Node Redis Cluster Deployment
        // =====================================================================
        Set<HostAndPort> clusterNodes = Set.of(
                new HostAndPort("127.0.0.1", 7001),
                new HostAndPort("127.0.0.1", 7002),
                new HostAndPort("127.0.0.1", 7003)
        );
        JedisCluster jedisCluster = new JedisCluster(clusterNodes);
        RedisConnectionProvider clusterProvider = new RedisConnectionProvider.Cluster(jedisCluster);

        try (DistributedRateLimiter clusterLimiter = new ProductionRedisRca70Limiter(clusterProvider, standaloneConfig)) {
            LimiterDecision clusterDecision = clusterLimiter.tryAcquire("tenant_enterprise_01", 100);
            System.out.printf("Cluster Decision: %s%n", clusterDecision.status());
        }
    }
}
