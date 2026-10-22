package com.dreb.ratelimit;

/**
 * Strategy for accruing and spending the idle-reward credit described in
 * Section 4.2 of the paper. Injected into {@link DREBLimiter} so the
 * accrual/decay curve can be swapped (e.g. for an exponential variant)
 * without touching {@code allow()}.
 */
public interface IdleCreditPolicy {

    /**
     * Grows {@code state.idleCredit} for an elapsed interval {@code dt}
     * during which the burst bucket has been sitting at full capacity for {@code tFull} seconds.
     * Also decays time-based attributes (e.g. abuseScore over {@code dt}).
     * Implementations should be idempotent no-ops when {@code tFull <= 0}.
     */
    void accrue(ClientState state, double dt, double tFull);

    /**
     * Debits {@code state.idleCredit} to cover a shortfall of
     * {@code deficit} tokens borrowed from the burst bucket.
     */
    void spend(ClientState state, double deficit);

    /**
     * Returns the effective burst tokens that the available idle credit can provide,
     * accounting for the debit multiplier / decay rate.
     */
    double effectiveCredit(ClientState state);
}
