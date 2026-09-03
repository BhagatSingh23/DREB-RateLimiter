package com.dreb.ratelimit;

/**
 * DREB v2. Extends v1's design with abuse-adaptive cost on BOTH idle credit
 * (via {@link AdaptiveIdleCreditPolicy}) and, critically, the sustained
 * bucket itself.
 *
 * <p>Testing v1 revealed that idle credit is a finite, depletable pool: an
 * adaptive decay rate only changes how fast it drains, not whether it
 * recovers between repeated cycles (accrual is gated on the burst bucket
 * being full, which a repeat abuser's bucket rarely is). Once idle credit
 * is drained -- typically within the first 1-2 abuse cycles -- any
 * idle-credit-only adaptation becomes irrelevant for the remaining cycles,
 * and behavior collapses to being governed purely by the sustained
 * bucket's flat 1:1 cost, identical to v1.
 *
 * <p>v2 therefore also makes the sustained bucket's cost per request
 * abuse-adaptive: a client with no recent history of drawing on idle
 * credit (abuseScore ~ 0) pays exactly {@code cost} sustained tokens per
 * request -- identical to Token Bucket's own rate, no penalty. A client
 * that has been repeatedly drawing on idle credit pays MORE than
 * {@code cost} sustained tokens per request, which persists across
 * however many cycles the abuse continues, unlike the idle-credit-only
 * mechanism.
 */
public final class DREBLimiterV2 extends AbstractRateLimiter {

    private final ConstantRateRefillStrategy refillStrategy;
    private final AdaptiveIdleCreditPolicy idlePolicy;
    private final double sustainedCapacity;
    private final double sustainedCostGain;

    public DREBLimiterV2(StateStore store, Clock clock,
                          ConstantRateRefillStrategy refillStrategy,
                          AdaptiveIdleCreditPolicy idlePolicy,
                          double sustainedCapacity,
                          double sustainedCostGain) {
        super(store, clock);
        this.refillStrategy = refillStrategy;
        this.idlePolicy = idlePolicy;
        this.sustainedCapacity = sustainedCapacity;
        this.sustainedCostGain = sustainedCostGain;
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

        // Well-behaved clients (abuseScore ~ 0) pay exactly `cost` --
        // identical to Token Bucket. Detected abusers pay more.
        double sustainedCost = cost * (1.0 + sustainedCostGain * state.abuseScore);

        if (state.sustainedTokens >= sustainedCost && effectiveBurst >= cost) {
            state.sustainedTokens -= sustainedCost;
            double deficit = Math.max(0.0, cost - state.burstTokens);
            state.burstTokens = Math.max(0.0, state.burstTokens - cost);
            idlePolicy.spend(state, deficit);
            return Decision.ALLOW;
        }
        return Decision.DENY;
    }
}
