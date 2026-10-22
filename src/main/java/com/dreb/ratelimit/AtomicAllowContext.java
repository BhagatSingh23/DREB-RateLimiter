package com.dreb.ratelimit;

/**
 * Parameters passed to atomic rate-limiting backends (e.g. Redis Lua scripts).
 */
public record AtomicAllowContext(
        double burstMax,
        double burstRate,
        double sustainedMax,
        double sustainedRate,
        double idleGain,
        double idleCap,
        double baseDecay,
        double abuseGain,
        double abuseIncrement,
        double abuseDecayRate,
        double sustainedCostGain,
        boolean legacyOverdraw,
        boolean decayScalingEnabled,
        boolean sustainedCostScalingEnabled
) {
    public static AtomicAllowContext forDrebV1(
            double burstMax, double burstRate,
            double sustainedMax, double sustainedRate,
            double idleGain, double idleCap, double idleDecay,
            boolean legacyOverdraw) {
        return new AtomicAllowContext(
                burstMax, burstRate, sustainedMax, sustainedRate,
                idleGain, idleCap, idleDecay, 0.0, 0.0, 0.0, 0.0,
                legacyOverdraw, false, false);
    }

    public static AtomicAllowContext forDrebV2(
            double burstMax, double burstRate,
            double sustainedMax, double sustainedRate,
            double idleGain, double idleCap,
            double baseDecay, double abuseGain, double abuseIncrement, double abuseDecayRate,
            double sustainedCostGain,
            boolean decayScalingEnabled, boolean sustainedCostScalingEnabled,
            boolean legacyOverdraw) {
        return new AtomicAllowContext(
                burstMax, burstRate, sustainedMax, sustainedRate,
                idleGain, idleCap,
                baseDecay, abuseGain, abuseIncrement, abuseDecayRate,
                sustainedCostGain,
                legacyOverdraw, decayScalingEnabled, sustainedCostScalingEnabled);
    }

    public static AtomicAllowContext forTokenBucket(double capacity, double refillRate) {
        return new AtomicAllowContext(
                capacity, refillRate, capacity, refillRate,
                0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0,
                false, false, false);
    }
}
