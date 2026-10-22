package com.dreb.ratelimit;

import java.util.List;
import java.util.Map;

/**
 * Minimal abstraction over whatever Redis client the deployment uses
 * (Jedis, Lettuce, etc.), so {@link RedisStateStore} has no compile-time
 * dependency on a specific client library.
 */
public interface RedisHashClient {

    /** Equivalent to Redis HGETALL key; returns an empty map if the key does not exist. */
    Map<String, String> hgetAll(String key);

    /** Equivalent to Redis HSET key field value [field value ...]. */
    void hset(String key, Map<String, String> fields);

    /**
     * Executes a server-side Lua script atomically with the given keys and arguments.
     * Returns the integer result returned by the script.
     */
    default long eval(String script, List<String> keys, List<String> args) {
        throw new UnsupportedOperationException("eval is not implemented on this RedisHashClient");
    }
}
