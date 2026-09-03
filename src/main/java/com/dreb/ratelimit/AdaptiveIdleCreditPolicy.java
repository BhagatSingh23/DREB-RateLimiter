package com.dreb.ratelimit;

/**
 * DREB v2's core change from v1's {@link LinearIdleCreditPolicy}.
 *
 * <p><b>Problem this solves.</b> In v1, every draw on idle credit is
 * penalized at the same flat {@code idleDecay} rate (paper default 1.5),
 * regardless of whether the client is bursting for the first time after
 * genuine idleness (which the algorithm is explicitly designed to reward)
 * or repeatedly re-drawing on idle credit across many cycles (which is the
 * abuse pattern the sustained bucket is meant to catch). Flat decay
 * under-rewards the good case and, empirically, does not always
 * out-penalize the bad case relative to a plain Token Bucket at the same
 * configured capacity (see the burst-ceiling-matched mapping's result on
 * repeated_burst_abuser, where v1 accepted slightly *more* traffic than
 * Token Bucket -- the opposite of the intended behavior).
 *
 * <p><b>Fix.</b> Track a per-client {@code abuseScore} (see
 * {@link ClientState#abuseScore}) that rises when idle credit is drawn on
 * and decays over time otherwise. The decay rate actually applied to a
 * given draw is {@code baseDecay + abuseGain * abuseScore}: near
 * {@code baseDecay} (intended to be close to 1.0 -- little to no penalty)
 * for a client with no recent history of drawing on idle credit, rising
 * for a client that keeps doing it. This makes the penalty track the
 * *pattern*, not just the *act*, of drawing on idle credit -- a single
 * legitimate idle-then-burst episode is cheap; a repeated one gets
 * progressively more expensive.
 */
public final class AdaptiveIdleCreditPolicy implements IdleCreditPolicy {

    private final double gain;
    private final double cap;
    private final double baseDecay;
    private final double abuseGain;
    private final double abuseIncrement;
    private final double abuseDecayRate;

    /**
     * @param gain            idle_credit accrual rate while burst bucket is full (per second)
     * @param cap             maximum idle_credit
     * @param baseDecay       decay applied to a draw with zero recent abuse history (~1.0 recommended)
     * @param abuseGain       how much each unit of abuseScore adds to the effective decay rate
     * @param abuseIncrement  how much abuseScore rises per unit of idle credit drawn
     * @param abuseDecayRate  how fast abuseScore forgets past draws, per second of elapsed time
     */
    public AdaptiveIdleCreditPolicy(double gain, double cap, double baseDecay,
                                     double abuseGain, double abuseIncrement, double abuseDecayRate) {
        this.gain = gain;
        this.cap = cap;
        this.baseDecay = baseDecay;
        this.abuseGain = abuseGain;
        this.abuseIncrement = abuseIncrement;
        this.abuseDecayRate = abuseDecayRate;
    }

    @Override
    public void accrue(ClientState state, double dt, boolean burstBucketFull) {
        if (burstBucketFull) {
            state.idleCredit = Math.min(cap, state.idleCredit + dt * gain);
        }
        // abuseScore forgets past draws over elapsed time regardless of
        // whether the burst bucket happens to be full right now -- a
        // client that stops abusing and goes back to normal behavior
        // should gradually be treated as normal again.
        state.abuseScore = Math.max(0.0, state.abuseScore - dt * abuseDecayRate);
    }

    @Override
    public void spend(ClientState state, double deficit) {
        if (deficit > 0) {
            double effectiveDecay = baseDecay + abuseGain * state.abuseScore;
            state.idleCredit = Math.max(0.0, state.idleCredit - deficit * effectiveDecay);
            // Normalize by `cap` so abuseScore stays in a scale-invariant
            // range regardless of absolute capacity -- without this, a
            // single full draw episode at capacity=50000 raises abuseScore
            // ~500x more than the same *proportional* episode at
            // capacity=100, causing massive overcorrection at scale
            // (verified: this caused DREB-v2 to collapse to 13.5%
            // acceptance on repeated_burst_abuser at capacity=50000,
            // vs. the intended ~70% -- a real bug caught by testing at
            // stress-tier scale before handoff, not by inspection).
            state.abuseScore += abuseIncrement * (deficit / cap);
        }
    }
}
