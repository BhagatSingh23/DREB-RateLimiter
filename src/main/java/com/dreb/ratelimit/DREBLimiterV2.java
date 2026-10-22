package com.dreb.ratelimit;

/**
 * DREB v2. Extends v1's design with abuse-adaptive cost on BOTH idle credit
 * (via {@link AdaptiveIdleCreditPolicy}) and, critically, the sustained
 * bucket itself.
 */
public final class DREBLimiterV2 extends AbstractRateLimiter {

    private final ConstantRateRefillStrategy refillStrategy;
    private final AdaptiveIdleCreditPolicy idlePolicy;
    private final double sustainedCapacity;
    private final double sustainedCostGain;
    private final boolean sustainedCostScalingEnabled;
    private final boolean legacyOverdraw;

    public DREBLimiterV2(StateStore store, Clock clock,
                          ConstantRateRefillStrategy refillStrategy,
                          AdaptiveIdleCreditPolicy idlePolicy,
                          double sustainedCapacity,
                          double sustainedCostGain,
                          boolean sustainedCostScalingEnabled,
                          boolean legacyOverdraw) {
        super(store, clock);
        this.refillStrategy = refillStrategy;
        this.idlePolicy = idlePolicy;
        this.sustainedCapacity = sustainedCapacity;
        this.sustainedCostGain = sustainedCostGain;
        this.sustainedCostScalingEnabled = sustainedCostScalingEnabled;
        this.legacyOverdraw = legacyOverdraw;
    }

    public DREBLimiterV2(StateStore store, Clock clock,
                          ConstantRateRefillStrategy refillStrategy,
                          AdaptiveIdleCreditPolicy idlePolicy,
                          double sustainedCapacity,
                          double sustainedCostGain) {
        this(store, clock, refillStrategy, idlePolicy, sustainedCapacity, sustainedCostGain, true, false);
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

        // Well-behaved clients (abuseScore ~ 0) pay exactly `cost`.
        // Detected abusers pay more if sustained cost scaling is enabled.
        double sustainedCost = sustainedCostScalingEnabled
                ? cost * (1.0 + sustainedCostGain * state.abuseScore)
                : cost;

        if (state.sustainedTokens >= sustainedCost && effectiveBurst >= cost) {
            state.sustainedTokens -= sustainedCost;
            double deficit = Math.max(0.0, cost - state.burstTokens);
            state.burstTokens = Math.max(0.0, state.burstTokens - cost);
            idlePolicy.spend(state, deficit);
            return Decision.ALLOW;
        }
        return Decision.DENY;
    }

    @Override
    protected AtomicAllowContext atomicAllowContext() {
        return AtomicAllowContext.forDrebV2(
                refillStrategy.burstMax(), refillStrategy.burstRate(),
                sustainedCapacity, refillStrategy.sustainedRate(),
                idlePolicy.gain(), idlePolicy.cap(),
                idlePolicy.baseDecay(), idlePolicy.abuseGain(), idlePolicy.abuseIncrement(), idlePolicy.abuseDecayRate(),
                sustainedCostGain,
                idlePolicy.isDecayScalingEnabled(), sustainedCostScalingEnabled,
                legacyOverdraw);
    }

    public ConstantRateRefillStrategy refillStrategy() { return refillStrategy; }
    public AdaptiveIdleCreditPolicy idlePolicy() { return idlePolicy; }
    public double sustainedCapacity() { return sustainedCapacity; }
    public double sustainedCostGain() { return sustainedCostGain; }
    public boolean isSustainedCostScalingEnabled() { return sustainedCostScalingEnabled; }
    public boolean isLegacyOverdraw() { return legacyOverdraw; }
}
