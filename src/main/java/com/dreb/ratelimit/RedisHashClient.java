package com.dreb.ratelimit;

import java.util.Map;

/**
 * Minimal abstraction over whatever Redis client the deployment uses
 * (Jedis, Lettuce, etc.), so {@link RedisStateStore} has no compile-time
 * dependency on a specific client library. Implement this with a thin
 * adapter around your chosen client's HGETALL / HSET (or, for the atomic
 * production path described in Section 5.2 and 6 of the paper, an EVAL of
 * a single Lua script performing refill + check + consume + save
 * server-side in one round trip).
 */
public interface RedisHashClient {

    /** Equivalent to Redis HGETALL key; returns an empty map if the key does not exist. */
    Map<String, String> hgetAll(String key);

    /** Equivalent to Redis HSET key field value [field value ...]. */
    void hset(String key, Map<String, String> fields);
}
