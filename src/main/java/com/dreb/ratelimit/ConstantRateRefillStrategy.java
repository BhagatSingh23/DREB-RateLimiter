package com.dreb.ratelimit;

/**
 * Refills both buckets at fixed, configured rates up to their capacities.
 * This is the refill policy used by DREB in the paper (Section 4.2).
 */
public final class ConstantRateRefillStrategy implements RefillStrategy {

    private final double burstMax;
    private final double burstRate;
    private final double sustainedMax;
    private final double sustainedRate;

    public ConstantRateRefillStrategy(double burstMax, double burstRate,
                                       double sustainedMax, double sustainedRate) {
        this.burstMax = burstMax;
        this.burstRate = burstRate;
        this.sustainedMax = sustainedMax;
        this.sustainedRate = sustainedRate;
    }

    @Override
    public void refill(ClientState state, double dt) {
        state.sustainedTokens = Math.min(sustainedMax, state.sustainedTokens + dt * sustainedRate);
        state.burstTokens = Math.min(burstMax, state.burstTokens + dt * burstRate);
    }

    public double burstMax() {
        return burstMax;
    }
}
