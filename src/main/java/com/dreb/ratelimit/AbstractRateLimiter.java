package com.dreb.ratelimit;

/**
 * Fixes the invariant request-handling sequence — load, refill, check-and-
 * consume, persist — while deferring the refill and check/consume steps to
 * subclasses (Section 5.1, "Template Method" pattern). This keeps
 * {@link TokenBucketLimiter} and {@link DREBLimiter} directly comparable
 * and testable under one harness, since both extend this class and differ
 * only in the two overridden steps.
 */
public abstract class AbstractRateLimiter implements RateLimiter {

    protected final StateStore store;
    protected final Clock clock;

    protected AbstractRateLimiter(StateStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public final Decision allow(String clientId, int cost) {
        double now = clock.now();
        ClientState state = store.load(clientId, () -> initialState(now));

        double dt = Math.max(0.0, now - state.lastRefillTs);
        refill(state, dt);
        state.lastRefillTs = now;

        Decision decision = checkAndConsume(state, cost);
        store.save(clientId, state);
        return decision;
    }

    /** Builds the initial state for a client seen for the first time. */
    protected abstract ClientState initialState(double now);

    /** Refills {@code state}'s bucket(s) in place for elapsed interval {@code dt}. */
    protected abstract void refill(ClientState state, double dt);

    /** Checks whether {@code cost} can be afforded and, if so, debits it from {@code state}. */
    protected abstract Decision checkAndConsume(ClientState state, int cost);

    /**
     * Non-mutating introspection of a client's current stored state, for
     * callers (e.g. an adapter reporting remaining capacity) that need to
     * look without going through {@link #allow(String, int)}. Does not
     * refill or consume anything. For a client never seen before, this
     * creates and returns a fresh record as a side effect (consistent with
     * {@link #allow}'s own first-touch behavior) — it does not fabricate
     * state for an existing client.
     */
    public final ClientState peekState(String clientId) {
        return store.load(clientId, () -> initialState(clock.now()));
    }
}
