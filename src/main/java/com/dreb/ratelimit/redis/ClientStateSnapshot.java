package com.dreb.ratelimit.redis;

/**
 * Snapshot of rate-limited state stored in Redis.
 */
public record ClientStateSnapshot(
        double burstTokens,
        double sustainedTokens,
        double idleCredit,
        double lastRefillTs,
        double abuseScore,
        long ttlSeconds
) {}
