package com.dreb.ratelimit;

/**
 * Centralizes the wiring of a concrete {@link RateLimiter}, its refill
 * strategy, its idle-credit policy, and its state store from a declarative
 * {@link PolicyConfig} (Section 5.1, "Factory" pattern). Keeps construction
 * logic out of call sites; adding a new algorithm or backend only requires
 * a new branch here, not changes at every place a limiter is created.
 */
public final class RateLimiterFactory {

    private final Clock clock;
    private final RedisHashClient redisClient; // may be null if Redis is unused

    public RateLimiterFactory(Clock clock, RedisHashClient redisClient) {
        this.clock = clock;
        this.redisClient = redisClient;
    }

    public RateLimiter create(PolicyConfig config) {
        StateStore store = config.useRedis
                ? new RedisStateStore(requireRedisClient(), "ratelimit")
                : new InMemoryStateStore();

        switch (config.algorithm) {
            case TOKEN_BUCKET:
                return new TokenBucketLimiter(store, clock, config.burstMax, config.burstRate);

            case DREB:
                ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(
                        config.burstMax, config.burstRate, config.sustainedMax, config.sustainedRate);
                IdleCreditPolicy idlePolicy = new LinearIdleCreditPolicy(
                        config.idleGain, config.idleCap, config.idleDecay);
                return new DREBLimiter(store, clock, refill, idlePolicy, config.sustainedMax);

            default:
                throw new IllegalArgumentException("Unknown algorithm: " + config.algorithm);
        }
    }

    private RedisHashClient requireRedisClient() {
        if (redisClient == null) {
            throw new IllegalStateException(
                    "PolicyConfig.useRedis is true but no RedisHashClient was supplied to the factory");
        }
        return redisClient;
    }
}
