package com.dreb.ratelimit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Distributed {@link StateStore} backed by Redis, so multiple gateway
 * instances share one view of each client's state.
 *
 * <p>Executes the entire refill-check-consume-save sequence in a single
 * atomic server-side Lua script via {@link #atomicAllow(String, int, double, AtomicAllowContext)}.
 * This provides strict distributed mutual exclusion and prevents race conditions
 * or lost updates when independent rate limiter instances handle requests for the same key.
 */
public final class RedisStateStore implements StateStore {

    private static final String F_BURST = "burst_tokens";
    private static final String F_SUSTAINED = "sustained_tokens";
    private static final String F_IDLE = "idle_credit";
    private static final String F_TS = "last_refill_ts";
    private static final String F_ABUSE = "abuse_score";

    public static final String DREB_ALLOW_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local cost = tonumber(ARGV[2])
            local burstMax = tonumber(ARGV[3])
            local burstRate = tonumber(ARGV[4])
            local sustainedMax = tonumber(ARGV[5])
            local sustainedRate = tonumber(ARGV[6])
            local idleGain = tonumber(ARGV[7])
            local idleCap = tonumber(ARGV[8])
            local baseDecay = tonumber(ARGV[9])
            local abuseGain = tonumber(ARGV[10])
            local abuseIncrement = tonumber(ARGV[11])
            local abuseDecayRate = tonumber(ARGV[12])
            local sustainedCostGain = tonumber(ARGV[13])
            local legacyOverdraw = tonumber(ARGV[14])
            local decayScalingEnabled = tonumber(ARGV[15])
            local sustainedCostScalingEnabled = tonumber(ARGV[16])

            local state = redis.call('HMGET', key, 'burst_tokens', 'sustained_tokens', 'idle_credit', 'last_refill_ts', 'abuse_score')
            local burstTokens = tonumber(state[1])
            local sustainedTokens = tonumber(state[2])
            local idleCredit = tonumber(state[3])
            local lastRefillTs = tonumber(state[4])
            local abuseScore = tonumber(state[5])

            if not burstTokens then
                burstTokens = burstMax
                sustainedTokens = sustainedMax
                idleCredit = 0.0
                lastRefillTs = now
                abuseScore = 0.0
            end

            local dt = math.max(0.0, now - lastRefillTs)
            if now >= lastRefillTs then
                lastRefillTs = now
            end

            if dt > 0.0 then
                local burstBefore = burstTokens
                sustainedTokens = math.min(sustainedMax, sustainedTokens + dt * sustainedRate)
                burstTokens = math.min(burstMax, burstBefore + dt * burstRate)

                local tFull = 0.0
                if burstBefore >= burstMax then
                    tFull = dt
                elseif burstRate > 0.0 then
                    local timeToFill = (burstMax - burstBefore) / burstRate
                    tFull = math.max(0.0, dt - timeToFill)
                end

                if tFull > 0.0 then
                    idleCredit = math.min(idleCap, idleCredit + tFull * idleGain)
                end
                if abuseDecayRate > 0.0 then
                    abuseScore = math.max(0.0, abuseScore - dt * abuseDecayRate)
                end
            end

            local effectiveDecay = baseDecay
            if decayScalingEnabled == 1 then
                effectiveDecay = baseDecay + abuseGain * abuseScore
            end

            local effectiveCredit = idleCredit
            if effectiveDecay > 0.0 then
                effectiveCredit = idleCredit / effectiveDecay
            end

            local effectiveBurst = burstTokens + effectiveCredit
            if legacyOverdraw == 1 then
                effectiveBurst = burstTokens + idleCredit
            end

            local sustainedCost = cost
            if sustainedCostScalingEnabled == 1 then
                sustainedCost = cost * (1.0 + sustainedCostGain * abuseScore)
            end

            local allowed = 0
            if sustainedTokens >= sustainedCost and effectiveBurst >= cost then
                allowed = 1
                sustainedTokens = sustainedTokens - sustainedCost
                local deficit = math.max(0.0, cost - burstTokens)
                burstTokens = math.max(0.0, burstTokens - cost)
                if deficit > 0.0 then
                    idleCredit = math.max(0.0, idleCredit - deficit * effectiveDecay)
                    if idleCap > 0.0 then
                        abuseScore = abuseScore + abuseIncrement * (deficit / idleCap)
                    end
                end
            end

            redis.call('HMSET', key,
                'burst_tokens', tostring(burstTokens),
                'sustained_tokens', tostring(sustainedTokens),
                'idle_credit', tostring(idleCredit),
                'last_refill_ts', tostring(lastRefillTs),
                'abuse_score', tostring(abuseScore)
            )

            return allowed
            """;

    private final RedisHashClient redis;
    private final String keyPrefix;

    public RedisStateStore(RedisHashClient redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean supportsAtomicAllow() {
        return true;
    }

    @Override
    public Decision atomicAllow(String clientId, int cost, double now, AtomicAllowContext context) {
        String key = key(clientId);
        List<String> keys = List.of(key);
        List<String> args = List.of(
                Double.toString(now),
                Integer.toString(cost),
                Double.toString(context.burstMax()),
                Double.toString(context.burstRate()),
                Double.toString(context.sustainedMax()),
                Double.toString(context.sustainedRate()),
                Double.toString(context.idleGain()),
                Double.toString(context.idleCap()),
                Double.toString(context.baseDecay()),
                Double.toString(context.abuseGain()),
                Double.toString(context.abuseIncrement()),
                Double.toString(context.abuseDecayRate()),
                Double.toString(context.sustainedCostGain()),
                context.legacyOverdraw() ? "1" : "0",
                context.decayScalingEnabled() ? "1" : "0",
                context.sustainedCostScalingEnabled() ? "1" : "0"
        );

        long res = redis.eval(DREB_ALLOW_LUA, keys, args);
        return (res == 1L) ? Decision.ALLOW : Decision.DENY;
    }

    @Override
    public ClientState load(String clientId, ClientStateInitializer initializer) {
        Map<String, String> fields = redis.hgetAll(key(clientId));
        if (fields == null || fields.isEmpty()) {
            return initializer.initial();
        }
        double abuse = 0.0;
        if (fields.containsKey(F_ABUSE)) {
            abuse = Double.parseDouble(fields.get(F_ABUSE));
        }
        return new ClientState(
                Double.parseDouble(fields.get(F_BURST)),
                Double.parseDouble(fields.get(F_SUSTAINED)),
                Double.parseDouble(fields.get(F_IDLE)),
                Double.parseDouble(fields.get(F_TS)),
                abuse);
    }

    @Override
    public void save(String clientId, ClientState state) {
        Map<String, String> fields = new HashMap<>();
        fields.put(F_BURST, Double.toString(state.burstTokens));
        fields.put(F_SUSTAINED, Double.toString(state.sustainedTokens));
        fields.put(F_IDLE, Double.toString(state.idleCredit));
        fields.put(F_TS, Double.toString(state.lastRefillTs));
        fields.put(F_ABUSE, Double.toString(state.abuseScore));
        redis.hset(key(clientId), fields);
    }

    private String key(String clientId) {
        return keyPrefix + ":" + clientId;
    }
}
