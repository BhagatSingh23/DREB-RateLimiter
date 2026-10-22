package com.dreb.ratelimit;

/**
 * DREB v2's core change from v1's {@link LinearIdleCreditPolicy}.
 *
 * <p>Tracks a per-client {@code abuseScore} (see {@link ClientState#abuseScore})
 * that rises when idle credit is drawn on and decays over time otherwise.
 * The decay rate applied to a draw is {@code baseDecay + abuseGain * abuseScore}
 * when decay scaling is enabled, or {@code baseDecay} when disabled.
 */
public final class AdaptiveIdleCreditPolicy implements IdleCreditPolicy {

    private final double gain;
    private final double cap;
    private final double baseDecay;
    private final double abuseGain;
    private final double abuseIncrement;
    private final double abuseDecayRate;
    private final boolean decayScalingEnabled;

    /**
     * @param gain                idle_credit accrual rate while burst bucket is full (per second)
     * @param cap                 maximum idle_credit
     * @param baseDecay           decay applied to a draw with zero recent abuse history (~1.0 recommended)
     * @param abuseGain           how much each unit of abuseScore adds to the effective decay rate
     * @param abuseIncrement      how much abuseScore rises per unit of idle credit drawn
     * @param abuseDecayRate      how fast abuseScore forgets past draws, per second of elapsed time
     * @param decayScalingEnabled whether decay scaling based on abuseScore is active (P5)
     */
    public AdaptiveIdleCreditPolicy(double gain, double cap, double baseDecay,
                                     double abuseGain, double abuseIncrement, double abuseDecayRate,
                                     boolean decayScalingEnabled) {
        this.gain = gain;
        this.cap = cap;
        this.baseDecay = baseDecay;
        this.abuseGain = abuseGain;
        this.abuseIncrement = abuseIncrement;
        this.abuseDecayRate = abuseDecayRate;
        this.decayScalingEnabled = decayScalingEnabled;
    }

    public AdaptiveIdleCreditPolicy(double gain, double cap, double baseDecay,
                                     double abuseGain, double abuseIncrement, double abuseDecayRate) {
        this(gain, cap, baseDecay, abuseGain, abuseIncrement, abuseDecayRate, true);
    }

    @Override
    public void accrue(ClientState state, double dt, double tFull) {
        if (tFull > 0) {
            state.idleCredit = Math.min(cap, state.idleCredit + tFull * gain);
        }
        // abuseScore forgets past draws over elapsed time regardless of
        // whether the burst bucket happens to be full right now
        if (dt > 0) {
            state.abuseScore = Math.max(0.0, state.abuseScore - dt * abuseDecayRate);
        }
    }

    @Override
    public void spend(ClientState state, double deficit) {
        if (deficit > 0) {
            double effectiveDecay = computeEffectiveDecay(state);
            state.idleCredit = Math.max(0.0, state.idleCredit - deficit * effectiveDecay);
            // Normalize by `cap` so abuseScore stays in a scale-invariant range
            if (cap > 0.0) {
                state.abuseScore += abuseIncrement * (deficit / cap);
            }
        }
    }

    @Override
    public double effectiveCredit(ClientState state) {
        double effectiveDecay = computeEffectiveDecay(state);
        return effectiveDecay > 0 ? (state.idleCredit / effectiveDecay) : state.idleCredit;
    }

    public double computeEffectiveDecay(ClientState state) {
        if (!decayScalingEnabled) {
            return baseDecay;
        }
        return baseDecay + abuseGain * state.abuseScore;
    }

    public double gain() { return gain; }
    public double cap() { return cap; }
    public double baseDecay() { return baseDecay; }
    public double abuseGain() { return abuseGain; }
    public double abuseIncrement() { return abuseIncrement; }
    public double abuseDecayRate() { return abuseDecayRate; }
    public boolean isDecayScalingEnabled() { return decayScalingEnabled; }
}
