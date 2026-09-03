package com.dreb.ratelimit;

/**
 * Public contract consumed by callers. Multiple algorithms (Token Bucket,
 * DREB, ...) sit behind this one interface so callers never depend on a
 * concrete algorithm (Section 5.1).
 */
public interface RateLimiter {

    /** Equivalent to {@code allow(clientId, 1)}. */
    default Decision allow(String clientId) {
        return allow(clientId, 1);
    }

    /**
     * Decides whether a request of the given {@code cost} from
     * {@code clientId} should be allowed, updating and persisting that
     * client's state as a side effect.
     */
    Decision allow(String clientId, int cost);
}
