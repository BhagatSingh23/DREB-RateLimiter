package com.dreb.ratelimit;

/**
 * The Dual-Rate Elastic Bucket algorithm (Section 4 of the paper).
 * Composes an injected {@link RefillStrategy} and {@link IdleCreditPolicy}
 * (Strategy pattern) so the refill and idle-reward mathematics can be
 * swapped independently of this class.
 */
public final class DREBLimiter extends AbstractRateLimiter {

    private final ConstantRateRefillStrategy refillStrategy;
    private final IdleCreditPolicy idlePolicy;
    private final double sustainedCapacity;

    public DREBLimiter(StateStore store, Clock clock,
                        ConstantRateRefillStrategy refillStrategy,
                        IdleCreditPolicy idlePolicy,
                        double sustainedCapacity) {
        super(store, clock);
        this.refillStrategy = refillStrategy;
        this.idlePolicy = idlePolicy;
        this.sustainedCapacity = sustainedCapacity;
    }

    @Override
    protected ClientState initialState(double now) {
        return ClientState.fresh(refillStrategy.burstMax(), sustainedCapacity, now);
    }

    @Override
    protected void refill(ClientState state, double dt) {
        refillStrategy.refill(state, dt);
        boolean burstBucketFull = state.burstTokens >= refillStrategy.burstMax();
        idlePolicy.accrue(state, dt, burstBucketFull);
    }

    @Override
    protected Decision checkAndConsume(ClientState state, int cost) {
        double effectiveBurst = state.burstTokens + state.idleCredit;

        if (state.sustainedTokens >= cost && effectiveBurst >= cost) {
            state.sustainedTokens -= cost;
            double deficit = Math.max(0.0, cost - state.burstTokens);
            state.burstTokens = Math.max(0.0, state.burstTokens - cost);
            idlePolicy.spend(state, deficit);
            return Decision.ALLOW;
        }
        return Decision.DENY;
    }
}
