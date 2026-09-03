package com.dreb.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-node, in-process {@link StateStore}. Appropriate for a single
 * server instance; for multiple instances behind a load balancer, use
 * {@link RedisStateStore} instead so all instances observe the same state.
 */
public final class InMemoryStateStore implements StateStore {

    private final ConcurrentHashMap<String, ClientState> table = new ConcurrentHashMap<>();

    @Override
    public ClientState load(String clientId, ClientStateInitializer initializer) {
        return table.computeIfAbsent(clientId, id -> initializer.initial());
    }

    @Override
    public void save(String clientId, ClientState state) {
        table.put(clientId, state);
    }

    /** Number of distinct clients currently tracked. Useful for tests/metrics. */
    public int size() {
        return table.size();
    }
}
