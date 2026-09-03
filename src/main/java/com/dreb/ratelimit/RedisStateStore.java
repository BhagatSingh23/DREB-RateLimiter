package com.dreb.ratelimit;

import java.util.HashMap;
import java.util.Map;

/**
 * Distributed {@link StateStore} backed by Redis, so multiple gateway
 * instances share one view of each client's state. This implementation
 * issues a read (HGETALL) then a write (HSET) per request; the paper
 * (Sections 5.2 and 6) notes that a production deployment should instead
 * push the whole refill/check/consume/save sequence into a single Lua
 * script via {@code EVAL} so it runs atomically, server-side, in one round
 * trip, which is what makes the design safe under concurrent requests for
 * the same client key. Swap {@link #load}/{@link #save} for a single
 * {@code evalAllow(...)} call to get that behavior.
 */
public final class RedisStateStore implements StateStore {

    private static final String F_BURST = "burst_tokens";
    private static final String F_SUSTAINED = "sustained_tokens";
    private static final String F_IDLE = "idle_credit";
    private static final String F_TS = "last_refill_ts";

    private final RedisHashClient redis;
    private final String keyPrefix;

    public RedisStateStore(RedisHashClient redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public ClientState load(String clientId, ClientStateInitializer initializer) {
        Map<String, String> fields = redis.hgetAll(key(clientId));
        if (fields == null || fields.isEmpty()) {
            return initializer.initial();
        }
        return new ClientState(
                Double.parseDouble(fields.get(F_BURST)),
                Double.parseDouble(fields.get(F_SUSTAINED)),
                Double.parseDouble(fields.get(F_IDLE)),
                Double.parseDouble(fields.get(F_TS)));
    }

    @Override
    public void save(String clientId, ClientState state) {
        Map<String, String> fields = new HashMap<>();
        fields.put(F_BURST, Double.toString(state.burstTokens));
        fields.put(F_SUSTAINED, Double.toString(state.sustainedTokens));
        fields.put(F_IDLE, Double.toString(state.idleCredit));
        fields.put(F_TS, Double.toString(state.lastRefillTs));
        redis.hset(key(clientId), fields);
    }

    private String key(String clientId) {
        return keyPrefix + ":" + clientId;
    }
}
