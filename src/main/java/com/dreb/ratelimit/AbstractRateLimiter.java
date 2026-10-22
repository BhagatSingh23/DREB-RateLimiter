package com.dreb.ratelimit;

/**
 * Fixes the invariant request-handling sequence — load, refill, check-and-
 * consume, persist — while deferring the refill and check/consume steps to
 * subclasses (Section 5.1, "Template Method" pattern). This keeps
 * {@link TokenBucketLimiter} and {@link DREBLimiter} directly comparable
 * and testable under one harness, since both extend this class and differ
 * only in the two overridden steps.
 *
 * <p>Thread safety: Synchronization is performed on the per-client {@link ClientState}
 * instance returned by {@link StateStore#load(String, StateStore.ClientStateInitializer)},
 * guaranteeing atomic load-refill-check-consume-save per client key.
 *
 * <p>Clock rollback behavior: If {@code now < state.lastRefillTs}, the elapsed
 * interval {@code dt} is clamped to 0.0 without rewinding {@code state.lastRefillTs}.
 * This prevents artificial token windfalls when the clock resumes forward progression.
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
        if (clientId == null) {
            throw new NullPointerException("clientId must not be null");
        }
        if (cost <= 0) {
            throw new IllegalArgumentException("cost must be positive, got: " + cost);
        }
        double now = clock.now();
        if (Double.isNaN(now) || Double.isInfinite(now)) {
            throw new IllegalArgumentException("clock.now() must be finite, got: " + now);
        }

        if (store.supportsAtomicAllow()) {
            return store.atomicAllow(clientId, cost, now, atomicAllowContext());
        }

        ClientState state = store.load(clientId, () -> initialState(now));

        synchronized (state) {
            if (now < state.lastRefillTs) {
                // Clock rollback: clamp elapsed time to 0, preserve lastRefillTs so that
                // later forward time does not award unearned refill tokens.
                refill(state, 0.0);
            } else {
                double dt = now - state.lastRefillTs;
                refill(state, dt);
                state.lastRefillTs = now;
            }

            Decision decision = checkAndConsume(state, cost);
            store.save(clientId, state);
            return decision;
        }
    }

    /** Builds the initial state for a client seen for the first time. */
    protected abstract ClientState initialState(double now);

    /** Refills {@code state}'s bucket(s) in place for elapsed interval {@code dt}. */
    protected abstract void refill(ClientState state, double dt);

    /** Checks whether {@code cost} can be afforded and, if so, debits it from {@code state}. */
    protected abstract Decision checkAndConsume(ClientState state, int cost);

    /** Provides parameter context for atomic backends (e.g. Redis Lua). */
    protected abstract AtomicAllowContext atomicAllowContext();

    /**
     * Non-mutating introspection of a client's current stored state, for
     * callers (e.g. an adapter reporting remaining capacity) that need to
     * look without going through {@link #allow(String, int)}. Does not
     * refill or consume anything. Returns a defensive snapshot of the state.
     */
    public final ClientState peekState(String clientId) {
        if (clientId == null) {
            throw new NullPointerException("clientId must not be null");
        }
        double now = clock.now();
        ClientState state = store.load(clientId, () -> initialState(now));
        synchronized (state) {
            return new ClientState(state);
        }
    }
}
