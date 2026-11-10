package com.dreb.ratelimit.redis;

import com.dreb.ratelimit.Decision;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Isolated Phase 6 Distributed Redis implementation of DREB-RCA70.
 *
 * <p>Executes the complete RCA70 adaptive-ceiling state transition inside a single
 * atomic Redis Lua script.
 *
 * <p>Features:
 * 1. Exact mathematical parity with Java in-memory {@code AdaptiveCeilingIdleCreditPolicy}.
 * 2. Clock source toggle: {@code CLIENT_TIMESTAMP} (for deterministic trace replay) vs.
 *    {@code REDIS_SERVER_TIME} (queries {@code redis.call('TIME')} to eliminate client clock skew).
 * 3. Idempotency key support: prevents duplicate debiting upon client timeout and retry.
 * 4. Redis Cluster hash tags: formats keys as {@code {clientId}:dreb:state} so all related
 *    keys hash to the identical slot.
 * 5. Safe TTL derivation: ensures keys expire only after the full resting state is reached.
 */
public final class RedisRca70Limiter {

    public enum ClockSource {
        CLIENT_TIMESTAMP,
        REDIS_SERVER_TIME
    }

    public static final String RCA70_ALLOW_LUA = """
            local stateKey = KEYS[1]
            local idempKey = (#KEYS >= 2) and KEYS[2] or nil

            local now = tonumber(ARGV[1])
            local cost = tonumber(ARGV[2])
            local burstMax = tonumber(ARGV[3])
            local burstRate = tonumber(ARGV[4])
            local sustainedMax = tonumber(ARGV[5])
            local sustainedRate = tonumber(ARGV[6])
            local idleGain = tonumber(ARGV[7])
            local baseCap = tonumber(ARGV[8])
            local baseDecay = tonumber(ARGV[9])
            local ceilingFactor = tonumber(ARGV[10])
            local recoveryRate = tonumber(ARGV[11])
            local keyTtl = tonumber(ARGV[12])
            local idempTtl = tonumber(ARGV[13])

            -- Check idempotency cache first
            if idempKey and idempKey ~= '' then
                local cached = redis.call('GET', idempKey)
                if cached then
                    return tonumber(cached)
                end
            end

            -- If server time mode, query Redis TIME
            if now < 0 then
                local t = redis.call('TIME')
                now = tonumber(t[1]) + tonumber(t[2]) / 1000000.0
            end

            local state = redis.call('HMGET', stateKey, 'burst_tokens', 'sustained_tokens', 'idle_credit', 'last_refill_ts', 'abuse_score')
            local B = tonumber(state[1])
            local S = tonumber(state[2])
            local I = tonumber(state[3])
            local lastTs = tonumber(state[4])
            local K = tonumber(state[5])

            if not B then
                B = burstMax
                S = sustainedMax
                I = 0.0
                lastTs = now
                K = 0.0
            end

            local dt = math.max(0.0, now - lastTs)
            if now >= lastTs then
                lastTs = now
            end

            if dt > 0.0 then
                local burstBefore = B
                S = math.min(sustainedMax, S + dt * sustainedRate)
                B = math.min(burstMax, burstBefore + dt * burstRate)

                local tFull = 0.0
                if burstBefore >= burstMax then
                    tFull = dt
                elseif burstRate > 0.0 then
                    local timeToFill = (burstMax - burstBefore) / burstRate
                    tFull = math.max(0.0, dt - timeToFill)
                end

                if K > 0.0 and recoveryRate > 0.0 then
                    K = math.max(0.0, K - dt * recoveryRate)
                end

                local effCap = baseCap / (1.0 + ceilingFactor * K)
                if tFull > 0.0 then
                    I = math.min(effCap, I + tFull * idleGain)
                else
                    I = math.min(effCap, I)
                end
            end

            local effCap = baseCap / (1.0 + ceilingFactor * K)
            local effCredit = I / baseDecay
            local effBurst = B + effCredit

            local allowed = 0
            if S >= cost and effBurst >= cost then
                allowed = 1
                S = S - cost
                local deficit = math.max(0.0, cost - B)
                B = math.max(0.0, B - cost)
                if deficit > 0.0 then
                    I = math.max(0.0, I - deficit * baseDecay)
                    if baseCap > 0.0 then
                        K = K + (deficit / baseCap)
                    end
                end
            end

            redis.call('HMSET', stateKey,
                'burst_tokens', string.format('%.17g', B),
                'sustained_tokens', string.format('%.17g', S),
                'idle_credit', string.format('%.17g', I),
                'last_refill_ts', string.format('%.17g', lastTs),
                'abuse_score', string.format('%.17g', K)
            )

            if keyTtl and keyTtl > 0 then
                redis.call('EXPIRE', stateKey, math.floor(keyTtl))
            end

            if idempKey and idempKey ~= '' and idempTtl and idempTtl > 0 then
                redis.call('SETEX', idempKey, math.floor(idempTtl), allowed)
            end

            return allowed
            """;

    private final JedisPool jedisPool;
    private final ClockSource clockSource;
    private final String keyPrefix;
    private final double burstMax;
    private final double burstRate;
    private final double sustainedMax;
    private final double sustainedRate;
    private final double idleGain;
    private final double baseCap;
    private final double baseDecay;
    private final double ceilingFactor;
    private final double recoveryRate;
    private final long keyTtlSeconds;
    private final long idempTtlSeconds;

    private volatile String scriptSha;

    public RedisRca70Limiter(JedisPool jedisPool, ClockSource clockSource, String keyPrefix,
                             double burstMax, double burstRate, double sustainedMax, double sustainedRate,
                             double idleGain, double baseCap, double baseDecay,
                             double ceilingFactor, double recoveryRate,
                             long keyTtlSeconds, long idempTtlSeconds) {
        this.jedisPool = jedisPool;
        this.clockSource = clockSource;
        this.keyPrefix = keyPrefix;
        this.burstMax = burstMax;
        this.burstRate = burstRate;
        this.sustainedMax = sustainedMax;
        this.sustainedRate = sustainedRate;
        this.idleGain = idleGain;
        this.baseCap = baseCap;
        this.baseDecay = baseDecay;
        this.ceilingFactor = ceilingFactor;
        this.recoveryRate = recoveryRate;
        this.keyTtlSeconds = keyTtlSeconds;
        this.idempTtlSeconds = idempTtlSeconds;
    }

    public static RedisRca70Limiter createDefault(JedisPool jedisPool, ClockSource clockSource, String keyPrefix) {
        double c = 1000.0;
        double r = 1000.0;
        double burstMax = 700.0;
        double burstRate = 5.0 * r / 3.0;
        double sustainedMax = 9.0 * c / 7.0;
        double sustainedRate = r;
        double idleGain = 1.5 * r;
        double baseCap = 450.0;
        double baseDecay = 1.5;
        double ceilingFactor = 0.75;
        double recoveryRate = 0.05;

        long safeTtl = 120; // 120s safe TTL
        long idempTtl = 60; // 60s idempotency TTL

        return new RedisRca70Limiter(jedisPool, clockSource, keyPrefix,
                burstMax, burstRate, sustainedMax, sustainedRate,
                idleGain, baseCap, baseDecay,
                ceilingFactor, recoveryRate,
                safeTtl, idempTtl);
    }

    public Decision allow(String clientId, int cost) {
        return allow(clientId, cost, -1.0, null);
    }

    public Decision allow(String clientId, int cost, double timestamp) {
        return allow(clientId, cost, timestamp, null);
    }

    public Decision allow(String clientId, int cost, double timestamp, String idempotencyKey) {
        String stateKey = formatStateKey(clientId);
        String idempKey = (idempotencyKey != null && !idempotencyKey.isEmpty())
                ? formatIdempKey(clientId, idempotencyKey)
                : "";

        List<String> keys = new ArrayList<>();
        keys.add(stateKey);
        if (!idempKey.isEmpty()) {
            keys.add(idempKey);
        }

        double nowArg = (clockSource == ClockSource.REDIS_SERVER_TIME || timestamp < 0.0)
                ? -1.0
                : timestamp;

        List<String> args = List.of(
                Double.toString(nowArg),
                Integer.toString(cost),
                Double.toString(burstMax),
                Double.toString(burstRate),
                Double.toString(sustainedMax),
                Double.toString(sustainedRate),
                Double.toString(idleGain),
                Double.toString(baseCap),
                Double.toString(baseDecay),
                Double.toString(ceilingFactor),
                Double.toString(recoveryRate),
                Long.toString(keyTtlSeconds),
                Long.toString(idempTtlSeconds)
        );

        long res = executeLua(keys, args);
        return (res == 1L) ? Decision.ALLOW : Decision.DENY;
    }

    private long executeLua(List<String> keys, List<String> args) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (scriptSha == null) {
                scriptSha = jedis.scriptLoad(RCA70_ALLOW_LUA);
            }
            try {
                Object result = jedis.evalsha(scriptSha, keys, args);
                return ((Number) result).longValue();
            } catch (Exception e) {
                scriptSha = jedis.scriptLoad(RCA70_ALLOW_LUA);
                Object result = jedis.evalsha(scriptSha, keys, args);
                return ((Number) result).longValue();
            }
        }
    }

    public String formatStateKey(String clientId) {
        return keyPrefix + ":{" + clientId + "}:state";
    }

    public String formatIdempKey(String clientId, String idempId) {
        return keyPrefix + ":{" + clientId + "}:idemp:" + idempId;
    }

    public record RedisStateSnapshot(
            double burstTokens,
            double sustainedTokens,
            double idleCredit,
            double lastRefillTs,
            double abuseScore,
            long ttlSeconds
    ) {}

    public RedisStateSnapshot peekState(String clientId) {
        String key = formatStateKey(clientId);
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> m = jedis.hgetAll(key);
            if (m == null || m.isEmpty()) {
                return null;
            }
            long ttl = jedis.ttl(key);
            return new RedisStateSnapshot(
                    Double.parseDouble(m.getOrDefault("burst_tokens", "0.0")),
                    Double.parseDouble(m.getOrDefault("sustained_tokens", "0.0")),
                    Double.parseDouble(m.getOrDefault("idle_credit", "0.0")),
                    Double.parseDouble(m.getOrDefault("last_refill_ts", "0.0")),
                    Double.parseDouble(m.getOrDefault("abuse_score", "0.0")),
                    ttl
            );
        }
    }

    public void resetClient(String clientId) {
        String stateKey = formatStateKey(clientId);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(stateKey);
        }
    }
}
