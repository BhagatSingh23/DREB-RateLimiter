package com.dreb.ratelimit;

/**
 * Linear idle-credit accrual and decay, as specified in Section 4.2:
 * idle_credit grows at {@code gain} per second while the burst bucket is
 * full, capped at {@code cap}, and is debited at {@code decay} times the
 * amount borrowed (decay &gt; 1 makes the bonus self-limiting).
 */
public final class LinearIdleCreditPolicy implements IdleCreditPolicy {

    private final double gain;
    private final double cap;
    private final double decay;

    public LinearIdleCreditPolicy(double gain, double cap, double decay) {
        this.gain = gain;
        this.cap = cap;
        this.decay = decay;
    }

    @Override
    public void accrue(ClientState state, double dt, double tFull) {
        if (tFull > 0) {
            state.idleCredit = Math.min(cap, state.idleCredit + tFull * gain);
        }
    }

    @Override
    public void spend(ClientState state, double deficit) {
        if (deficit > 0) {
            state.idleCredit = Math.max(0.0, state.idleCredit - deficit * decay);
        }
    }

    @Override
    public double effectiveCredit(ClientState state) {
        if (decay <= 0) {
            return state.idleCredit;
        }
        return state.idleCredit / decay;
    }

    public double gain() { return gain; }
    public double cap() { return cap; }
    public double decay() { return decay; }
}
