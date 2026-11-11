package com.dreb.ratelimit.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisNoScriptException;

import java.io.Closeable;
import java.util.List;
import java.util.Map;

/**
 * Abstraction for executing Redis operations over standalone or cluster connections.
 */
public interface RedisConnectionProvider extends Closeable {

    Object evalLua(String scriptSha, String scriptText, List<String> keys, List<String> args);

    Map<String, String> hgetAll(String key);

    long ttl(String key);

    void del(String... keys);

    boolean ping();

    @Override
    void close();

    /**
     * Standalone Redis provider backed by JedisPool.
     */
    final class Standalone implements RedisConnectionProvider {
        private final JedisPool jedisPool;

        public Standalone(JedisPool jedisPool) {
            this.jedisPool = jedisPool;
        }

        @Override
        public Object evalLua(String scriptSha, String scriptText, List<String> keys, List<String> args) {
            try (Jedis jedis = jedisPool.getResource()) {
                try {
                    return jedis.evalsha(scriptSha, keys, args);
                } catch (JedisNoScriptException e) {
                    jedis.scriptLoad(scriptText);
                    return jedis.evalsha(scriptSha, keys, args);
                }
            }
        }

        @Override
        public Map<String, String> hgetAll(String key) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.hgetAll(key);
            }
        }

        @Override
        public long ttl(String key) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.ttl(key);
            }
        }

        @Override
        public void del(String... keys) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(keys);
            }
        }

        @Override
        public boolean ping() {
            try (Jedis jedis = jedisPool.getResource()) {
                return "PONG".equalsIgnoreCase(jedis.ping());
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void close() {
            if (jedisPool != null && !jedisPool.isClosed()) {
                jedisPool.close();
            }
        }
    }

    /**
     * Cluster Redis provider backed by JedisCluster.
     */
    final class Cluster implements RedisConnectionProvider {
        private final JedisCluster jedisCluster;

        public Cluster(JedisCluster jedisCluster) {
            this.jedisCluster = jedisCluster;
        }

        @Override
        public Object evalLua(String scriptSha, String scriptText, List<String> keys, List<String> args) {
            String sampleKey = keys.get(0);
            try {
                return jedisCluster.evalsha(scriptSha, keys, args);
            } catch (JedisNoScriptException e) {
                jedisCluster.scriptLoad(scriptText, sampleKey);
                return jedisCluster.evalsha(scriptSha, keys, args);
            }
        }

        @Override
        public Map<String, String> hgetAll(String key) {
            return jedisCluster.hgetAll(key);
        }

        @Override
        public long ttl(String key) {
            return jedisCluster.ttl(key);
        }

        @Override
        public void del(String... keys) {
            jedisCluster.del(keys);
        }

        @Override
        public boolean ping() {
            try {
                // In cluster, ping sample key or verify cluster nodes
                Map<String, ?> clusterNodes = jedisCluster.getClusterNodes();
                return clusterNodes != null && !clusterNodes.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void close() {
            if (jedisCluster != null) {
                jedisCluster.close();
            }
        }
    }
}
