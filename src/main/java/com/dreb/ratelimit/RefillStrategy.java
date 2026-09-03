package com.dreb.ratelimit;

/**
 * Strategy for refilling a client's burst and sustained buckets.
 * Injected into {@link DREBLimiter} so the refill mathematics can be
 * swapped without touching {@code allow()} or the persistence layer
 * (Section 5.1, "Strategy" pattern).
 */
public interface RefillStrategy {
    /**
     * Refills {@code state}'s burst and sustained buckets in place for the
     * elapsed interval {@code dt} (seconds).
     */
    void refill(ClientState state, double dt);
}
