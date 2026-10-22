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
    private final boolean legacyOverdraw;

    public DREBLimiter(StateStore store, Clock clock,
                        ConstantRateRefillStrategy refillStrategy,
                        IdleCreditPolicy idlePolicy,
                        double sustainedCapacity,
                        boolean legacyOverdraw) {
        super(store, clock);
        this.refillStrategy = refillStrategy;
        this.idlePolicy = idlePolicy;
        this.sustainedCapacity = sustainedCapacity;
        this.legacyOverdraw = legacyOverdraw;
    }

    public DREBLimiter(StateStore store, Clock clock,
                        ConstantRateRefillStrategy refillStrategy,
                        IdleCreditPolicy idlePolicy,
                        double sustainedCapacity) {
        this(store, clock, refillStrategy, idlePolicy, sustainedCapacity, false);
    }

    @Override
    protected ClientState initialState(double now) {
        return ClientState.fresh(refillStrategy.burstMax(), sustainedCapacity, now);
    }

    @Override
    protected void refill(ClientState state, double dt) {
        if (dt <= 0.0) {
            return;
        }
        double burstBefore = state.burstTokens;
        refillStrategy.refill(state, dt);
        double tFull;
        if (burstBefore >= refillStrategy.burstMax()) {
            tFull = dt;
        } else if (refillStrategy.burstRate() <= 0.0) {
            tFull = 0.0;
        } else {
            double timeToFill = (refillStrategy.burstMax() - burstBefore) / refillStrategy.burstRate();
            tFull = Math.max(0.0, dt - timeToFill);
        }
        idlePolicy.accrue(state, dt, tFull);
    }

    @Override
    protected Decision checkAndConsume(ClientState state, int cost) {
        double effectiveBurst = legacyOverdraw
                ? (state.burstTokens + state.idleCredit)
                : (state.burstTokens + idlePolicy.effectiveCredit(state));

        if (state.sustainedTokens >= cost && effectiveBurst >= cost) {
            state.sustainedTokens -= cost;
            double deficit = Math.max(0.0, cost - state.burstTokens);
            state.burstTokens = Math.max(0.0, state.burstTokens - cost);
            idlePolicy.spend(state, deficit);
            return Decision.ALLOW;
        }
        return Decision.DENY;
    }

    @Override
    protected AtomicAllowContext atomicAllowContext() {
        double decay = (idlePolicy instanceof LinearIdleCreditPolicy linear) ? linear.decay() : 1.5;
        double gain = (idlePolicy instanceof LinearIdleCreditPolicy linear) ? linear.gain() : 0.0;
        double cap = (idlePolicy instanceof LinearIdleCreditPolicy linear) ? linear.cap() : 0.0;
        return AtomicAllowContext.forDrebV1(
                refillStrategy.burstMax(), refillStrategy.burstRate(),
                sustainedCapacity, refillStrategy.sustainedRate(),
                gain, cap, decay, legacyOverdraw);
    }

    public ConstantRateRefillStrategy refillStrategy() { return refillStrategy; }
    public IdleCreditPolicy idlePolicy() { return idlePolicy; }
    public double sustainedCapacity() { return sustainedCapacity; }
    public boolean isLegacyOverdraw() { return legacyOverdraw; }
}
