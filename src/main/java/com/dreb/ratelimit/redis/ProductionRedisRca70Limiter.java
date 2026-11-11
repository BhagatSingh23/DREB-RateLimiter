package com.dreb.ratelimit.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Production-ready distributed DREB-RCA70 rate limiter for Redis (Standalone and Cluster).
 */
public final class ProductionRedisRca70Limiter implements DistributedRateLimiter {

    public static final String RCA70_PRODUCTION_LUA = """
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
            local activeStateTtl = tonumber(ARGV[12])
            local idempTtl = tonumber(ARGV[13])
            local payloadFingerprint = ARGV[14]
            local coldStartInitialIdle = tonumber(ARGV[15])

            -- 1. Atomic Idempotency Check with Payload Fingerprint Binding
            if idempKey and idempKey ~= '' then
                local cached = redis.call('GET', idempKey)
                if cached then
                    local sep1 = string.find(cached, ':')
                    if sep1 then
                        local cachedFp = string.sub(cached, 1, sep1 - 1)
                        if cachedFp ~= '' and payloadFingerprint ~= '' and cachedFp ~= payloadFingerprint then
                            return 'CONFLICT:' .. cached
                        end
                    end
                    return 'CACHED:' .. cached
                end
            end

            -- 2. Time query inside Redis if server-time mode
            if now < 0 then
                local t = redis.call('TIME')
                now = tonumber(t[1]) + tonumber(t[2]) / 1000000.0
            end

            -- 3. Read current state
            local state = redis.call('HMGET', stateKey, 'burst_tokens', 'sustained_tokens', 'idle_credit', 'last_refill_ts', 'abuse_score')
            local B = tonumber(state[1])
            local S = tonumber(state[2])
            local I = tonumber(state[3])
            local lastTs = tonumber(state[4])
            local K = tonumber(state[5])

            if not B then
                B = burstMax
                S = sustainedMax
                I = coldStartInitialIdle
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
            local reason = ''
            if S < cost then
                reason = 'SUSTAINED_LIMIT_EXCEEDED'
            elseif effBurst < cost then
                reason = 'BURST_LIMIT_EXCEEDED'
            else
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

            -- Safe Lifecycle: Only apply expiration if explicitly enabled (>0)
            if activeStateTtl and activeStateTtl > 0 then
                redis.call('EXPIRE', stateKey, math.floor(activeStateTtl))
            end

            local outPayload = string.format('%s:%d:%.4f:%.4f:%.4f:%.4f:%.6f:%s',
                payloadFingerprint, allowed, B, S, I, K, now, reason)

            if idempKey and idempKey ~= '' and idempTtl and idempTtl > 0 then
                redis.call('SETEX', idempKey, math.floor(idempTtl), outPayload)
            end

            return 'NEW:' .. outPayload
            """;

    private final RedisConnectionProvider connectionProvider;
    private final RedisLimiterConfig config;
    private final String scriptSha;

    public ProductionRedisRca70Limiter(RedisConnectionProvider connectionProvider, RedisLimiterConfig config) {
        this.connectionProvider = connectionProvider;
        this.config = (config == null) ? RedisLimiterConfig.createDefaultRca70() : config;
        this.scriptSha = computeSha1(RCA70_PRODUCTION_LUA);
    }

    public RedisLimiterConfig getConfig() {
        return config;
    }

    @Override
    public LimiterDecision tryAcquire(String clientId, int cost) {
        return tryAcquire(clientId, cost, null, null, -1.0);
    }

    @Override
    public LimiterDecision tryAcquire(String clientId, int cost, String idempotencyKey) {
        String fp = (idempotencyKey != null && !idempotencyKey.isEmpty()) ? "cost=" + cost : null;
        return tryAcquire(clientId, cost, idempotencyKey, fp, -1.0);
    }

    @Override
    public LimiterDecision tryAcquire(String clientId, int cost, String idempotencyKey, String payloadFingerprint) {
        return tryAcquire(clientId, cost, idempotencyKey, payloadFingerprint, -1.0);
    }

    /**
     * Package-private overload supporting synthetic timestamps for testing.
     */
    LimiterDecision tryAcquire(String clientId, int cost, String idempotencyKey, String payloadFingerprint, double timestamp) {
        if (cost < 0) {
            throw new IllegalArgumentException("Request cost must be non-negative: " + cost);
        }

        String stateKey = formatStateKey(clientId);
        String idempKey = (idempotencyKey != null && !idempotencyKey.isEmpty())
                ? formatIdempKey(clientId, idempotencyKey)
                : "";

        List<String> keys = new ArrayList<>();
        keys.add(stateKey);
        if (!idempKey.isEmpty()) {
            keys.add(idempKey);
        }

        double nowArg = (config.clockSource() == RedisLimiterConfig.ClockSource.REDIS_SERVER_TIME || timestamp < 0.0)
                ? -1.0
                : timestamp;

        double initialIdle = (config.coldStartPolicy() == RedisLimiterConfig.ColdStartPolicy.PERMISSIVE_FULL_BURST)
                ? config.baseCap()
                : 0.0;

        String fp = (payloadFingerprint == null) ? "" : payloadFingerprint;

        List<String> args = List.of(
                Double.toString(nowArg),
                Integer.toString(cost),
                Double.toString(config.burstMax()),
                Double.toString(config.burstRate()),
                Double.toString(config.sustainedMax()),
                Double.toString(config.sustainedRate()),
                Double.toString(config.idleGain()),
                Double.toString(config.baseCap()),
                Double.toString(config.baseDecay()),
                Double.toString(config.ceilingFactor()),
                Double.toString(config.recoveryRate()),
                Long.toString(config.activeStateTtlSeconds()),
                Long.toString(config.idempotencyTtlSeconds()),
                fp,
                Double.toString(initialIdle)
        );

        try {
            Object raw = connectionProvider.evalLua(scriptSha, RCA70_PRODUCTION_LUA, keys, args);
            String rawStr = (raw != null) ? raw.toString() : "";
            return parseDecision(rawStr, cost);
        } catch (Exception e) {
            boolean failOpen = (config.failureMode() == RedisLimiterConfig.FailureMode.FAIL_OPEN);
            return LimiterDecision.infrastructureError(cost, e.getClass().getSimpleName() + ": " + e.getMessage(), failOpen);
        }
    }

    private LimiterDecision parseDecision(String raw, int cost) {
        if (raw == null || raw.isEmpty()) {
            boolean failOpen = (config.failureMode() == RedisLimiterConfig.FailureMode.FAIL_OPEN);
            return LimiterDecision.infrastructureError(cost, "Empty Redis response", failOpen);
        }

        if (raw.startsWith("CONFLICT:")) {
            return LimiterDecision.conflict(cost, "Idempotency key reused with conflicting request payload fingerprint");
        }

        boolean isCached = raw.startsWith("CACHED:");
        int payloadStart = raw.indexOf(':');
        String payload = (payloadStart >= 0) ? raw.substring(payloadStart + 1) : raw;

        String[] parts = payload.split(":");
        if (parts.length < 7) {
            boolean failOpen = (config.failureMode() == RedisLimiterConfig.FailureMode.FAIL_OPEN);
            return LimiterDecision.infrastructureError(cost, "Malformed Redis response: " + raw, failOpen);
        }

        // parts: [0]=fingerprint, [1]=allowed, [2]=B, [3]=S, [4]=I, [5]=K, [6]=now, [7]=reason(optional)
        boolean allowed = "1".equals(parts[1]);
        double b = parseDoubleSafe(parts[2]);
        double s = parseDoubleSafe(parts[3]);
        double i = parseDoubleSafe(parts[4]);
        double k = parseDoubleSafe(parts[5]);
        double nowSec = parseDoubleSafe(parts[6]);
        long tsMicros = (long) (nowSec * 1_000_000.0);
        String reason = (parts.length >= 8 && !parts[7].isEmpty()) ? parts[7] : null;

        if (allowed) {
            return LimiterDecision.allow(cost, b, s, i, k, tsMicros, isCached);
        } else {
            return LimiterDecision.deny(cost, b, s, i, k, tsMicros, isCached, reason != null ? reason : "RATE_LIMIT_EXCEEDED");
        }
    }

    private static double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public ClientStateSnapshot getState(String clientId) {
        String key = formatStateKey(clientId);
        try {
            Map<String, String> m = connectionProvider.hgetAll(key);
            if (m == null || m.isEmpty()) {
                return null;
            }
            long ttl = connectionProvider.ttl(key);
            return new ClientStateSnapshot(
                    Double.parseDouble(m.getOrDefault("burst_tokens", "0.0")),
                    Double.parseDouble(m.getOrDefault("sustained_tokens", "0.0")),
                    Double.parseDouble(m.getOrDefault("idle_credit", "0.0")),
                    Double.parseDouble(m.getOrDefault("last_refill_ts", "0.0")),
                    Double.parseDouble(m.getOrDefault("abuse_score", "0.0")),
                    ttl
            );
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void resetState(String clientId) {
        String stateKey = formatStateKey(clientId);
        try {
            connectionProvider.del(stateKey);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isHealthy() {
        return connectionProvider.ping();
    }

    @Override
    public void close() {
        connectionProvider.close();
    }

    public String formatStateKey(String clientId) {
        return config.keyPrefix() + ":{" + clientId + "}:state";
    }

    public String formatIdempKey(String clientId, String idempId) {
        return config.keyPrefix() + ":{" + clientId + "}:idemp:" + idempId;
    }

    private static String computeSha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 not supported", e);
        }
    }
}
