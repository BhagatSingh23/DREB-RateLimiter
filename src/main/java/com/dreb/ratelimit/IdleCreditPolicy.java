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
     * during which the burst bucket has been sitting at full capacity.
     * Implementations should be idempotent no-ops when the burst bucket is
     * not full.
     */
    void accrue(ClientState state, double dt, boolean burstBucketFull);

    /**
     * Debits {@code state.idleCredit} to cover a shortfall of
     * {@code deficit} tokens borrowed from the burst bucket.
     */
    void spend(ClientState state, double deficit);
}
