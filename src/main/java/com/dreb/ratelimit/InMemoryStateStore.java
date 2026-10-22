package com.dreb.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-node, in-process {@link StateStore}. Appropriate for a single
 * server instance; for multiple instances behind a load balancer, use
 * {@link RedisStateStore} instead so all instances observe the same state.
 *
 * <p>Supports an optional time-to-live (TTL) to evict client records that
 * have been inactive for longer than the TTL threshold.
 */
public final class InMemoryStateStore implements StateStore {

    private final ConcurrentHashMap<String, ClientState> table = new ConcurrentHashMap<>();
    private final Duration stateTtl;

    /** Unbounded in-memory store without automated TTL eviction. */
    public InMemoryStateStore() {
        this(null);
    }

    /**
     * In-memory store with an inactive-state TTL.
     *
     * @param stateTtl maximum duration of inactivity before an entry may be evicted,
     *                 or {@code null} for no eviction.
     */
    public InMemoryStateStore(Duration stateTtl) {
        this.stateTtl = stateTtl;
    }

    @Override
    public ClientState load(String clientId, ClientStateInitializer initializer) {
        return table.computeIfAbsent(clientId, id -> initializer.initial());
    }

    @Override
    public void save(String clientId, ClientState state) {
        table.put(clientId, state);
    }

    /**
     * Looks up an existing client state without creating a new record if absent.
     */
    public ClientState loadIfPresent(String clientId) {
        return table.get(clientId);
    }

    /**
     * Evicts client entries whose {@code lastRefillTs} is older than {@code ttl}
     * relative to {@code nowSeconds}.
     *
     * @param nowSeconds current reference time in seconds
     * @return number of entries evicted
     */
    public int evictExpired(double nowSeconds) {
        if (stateTtl == null) {
            return 0;
        }
        double ttlSeconds = stateTtl.toMillis() / 1000.0;
        int count = 0;
        for (var entry : table.entrySet()) {
            ClientState state = entry.getValue();
            synchronized (state) {
                if ((nowSeconds - state.lastRefillTs) > ttlSeconds) {
                    if (table.remove(entry.getKey(), state)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Evicts a specific client entry. */
    public boolean evict(String clientId) {
        return table.remove(clientId) != null;
    }

    /** Clears all stored entries. */
    public void clear() {
        table.clear();
    }

    /** Number of distinct clients currently tracked. Useful for tests/metrics. */
    public int size() {
        return table.size();
    }

    public Duration getStateTtl() {
        return stateTtl;
    }
}
