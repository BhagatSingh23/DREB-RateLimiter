package com.dreb.ratelimit;

/**
 * Plain data object holding one client's rate-limiter state.
 *
 * Deliberately behavior-free (see Section 5.1 of the paper): RefillStrategy
 * and IdleCreditPolicy both operate on a ClientState without owning it,
 * which lets the same record be serialized directly into a Redis hash.
 */
public final class ClientState {

    /** Fast bucket; refills quickly. */
    public double burstTokens;

    /** Slow bucket; bounds the long-run average rate. */
    public double sustainedTokens;

    /** Earned bonus burst capacity, accrued while burstTokens is full. */
    public double idleCredit;

    /**
     * Recency-weighted count of how much idle credit this client has drawn
     * on recently. Unused by v1's {@link LinearIdleCreditPolicy} (stays at
     * its default of 0); used by {@code AdaptiveIdleCreditPolicy} (v2) to
     * distinguish a client bursting for the first time after genuine
     * idleness from one repeatedly re-drawing on idle credit.
     */
    public double abuseScore = 0.0;

    /** Timestamp (seconds) of the last refill/update. */
    public double lastRefillTs;

    public ClientState(double burstTokens, double sustainedTokens,
                        double idleCredit, double lastRefillTs) {
        this.burstTokens = burstTokens;
        this.sustainedTokens = sustainedTokens;
        this.idleCredit = idleCredit;
        this.lastRefillTs = lastRefillTs;
    }

    /** Creates a freshly-initialized state with both buckets full and no idle credit. */
    public static ClientState fresh(double burstCapacity, double sustainedCapacity, double now) {
        return new ClientState(burstCapacity, sustainedCapacity, 0.0, now);
    }

    @Override
    public String toString() {
        return String.format(
                "ClientState{burst=%.3f, sustained=%.3f, idleCredit=%.3f, lastTs=%.3f}",
                burstTokens, sustainedTokens, idleCredit, lastRefillTs);
    }
}
