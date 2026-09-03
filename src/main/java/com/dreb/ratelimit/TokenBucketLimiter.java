package com.dreb.ratelimit;

/**
 * Plain Token Bucket, used in the paper (Section 7) as the baseline DREB is
 * compared against. A single bucket refills at {@code refillRate} tokens/sec
 * up to {@code capacity}; a request is allowed iff enough tokens are
 * available, in which case they are debited.
 */
public final class TokenBucketLimiter extends AbstractRateLimiter {

    private final double capacity;
    private final double refillRate;

    public TokenBucketLimiter(StateStore store, Clock clock, double capacity, double refillRate) {
        super(store, clock);
        this.capacity = capacity;
        this.refillRate = refillRate;
    }

    @Override
    protected ClientState initialState(double now) {
        // sustainedTokens/idleCredit are unused by this algorithm but kept
        // for a uniform ClientState shape across limiter implementations.
        return new ClientState(capacity, 0.0, 0.0, now);
    }

    @Override
    protected void refill(ClientState state, double dt) {
        state.burstTokens = Math.min(capacity, state.burstTokens + dt * refillRate);
    }

    @Override
    protected Decision checkAndConsume(ClientState state, int cost) {
        if (state.burstTokens >= cost) {
            state.burstTokens -= cost;
            return Decision.ALLOW;
        }
        return Decision.DENY;
    }
}
