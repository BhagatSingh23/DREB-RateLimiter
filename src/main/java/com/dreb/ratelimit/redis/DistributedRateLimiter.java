package com.dreb.ratelimit.redis;

/**
 * Production-facing interface for distributed DREB-RCA70 rate limiting on Redis.
 */
public interface DistributedRateLimiter extends AutoCloseable {

    /**
     * Attempts to acquire capacity of cost without an idempotency key.
     */
    LimiterDecision tryAcquire(String clientId, int cost);

    /**
     * Attempts to acquire capacity of cost with an idempotency key (fingerprint derived from cost).
     */
    LimiterDecision tryAcquire(String clientId, int cost, String idempotencyKey);

    /**
     * Attempts to acquire capacity of cost with an idempotency key and explicit payload fingerprint.
     */
    LimiterDecision tryAcquire(String clientId, int cost, String idempotencyKey, String payloadFingerprint);

    /**
     * Queries current rate limiter state snapshot from Redis.
     */
    ClientStateSnapshot getState(String clientId);

    /**
     * Resets state for a client ID in Redis.
     */
    void resetState(String clientId);

    /**
     * Health check verifying Redis connectivity.
     */
    boolean isHealthy();

    @Override
    void close();
}
