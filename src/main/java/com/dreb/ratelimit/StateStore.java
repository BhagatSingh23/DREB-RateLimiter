package com.dreb.ratelimit;

/**
 * Decouples decision logic from where client state physically lives
 * (Section 5.1, "Bridge" pattern). {@link InMemoryStateStore} and
 * {@link RedisStateStore} are interchangeable at deploy time, so the same
 * {@link DREBLimiter} instance runs unmodified on a single node or behind a
 * distributed, Redis-backed gateway.
 */
public interface StateStore {

    /**
     * Loads the state for {@code clientId}, creating a fresh one via
     * {@code initializer} if none exists yet.
     */
    ClientState load(String clientId, ClientStateInitializer initializer);

    /** Persists {@code state} for {@code clientId}. */
    void save(String clientId, ClientState state);

    /**
     * Whether this store supports atomic allow operations executed entirely on the storage backend
     * (e.g. via Redis Lua scripts).
     */
    default boolean supportsAtomicAllow() {
        return false;
    }

    /**
     * Executes atomic refill, check, consume, and save in a single operation.
     */
    default Decision atomicAllow(String clientId, int cost, double now, AtomicAllowContext context) {
        throw new UnsupportedOperationException("Atomic allow not supported by " + getClass().getName());
    }

    /** Factory for a brand-new client's initial state. */
    interface ClientStateInitializer {
        ClientState initial();
    }
}
