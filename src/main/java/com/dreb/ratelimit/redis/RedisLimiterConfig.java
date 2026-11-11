package com.dreb.ratelimit.redis;

/**
 * Configuration for the Production Redis DREB-RCA70 Rate Limiter.
 */
public final class RedisLimiterConfig {

    public enum ClockSource {
        REDIS_SERVER_TIME,
        CLIENT_TIMESTAMP
    }

    public enum ColdStartPolicy {
        /**
         * Conservative cold start: B=700, S=1285.71, I=0.0, K=0.0.
         * Prevents Sybil attackers from immediately bursting nominal capacity C=1000.
         * Trade-off: First request of cost 1000 is rejected until client idles 0.72s.
         */
        SECURE_CONSERVATIVE,

        /**
         * Permissive cold start: B=700, S=1285.71, I=450.0, K=0.0.
         * Allows legitimate new clients to execute nominal burst C=1000 immediately.
         * Trade-off: New client IDs can harvest up to 1000 tokens on first request.
         */
        PERMISSIVE_FULL_BURST
    }

    public enum FailureMode {
        /**
         * Fail closed: If Redis is unavailable, reject requests with INFRASTRUCTURE_ERROR.
         */
        FAIL_CLOSED,

        /**
         * Fail open: If Redis is unavailable, allow requests to prioritize upstream availability.
         */
        FAIL_OPEN
    }

    private final double burstMax;
    private final double burstRate;
    private final double sustainedMax;
    private final double sustainedRate;
    private final double idleGain;
    private final double baseCap;
    private final double baseDecay;
    private final double ceilingFactor;
    private final double recoveryRate;
    private final String keyPrefix;
    private final long activeStateTtlSeconds;
    private final long idempotencyTtlSeconds;
    private final ClockSource clockSource;
    private final ColdStartPolicy coldStartPolicy;
    private final FailureMode failureMode;

    public RedisLimiterConfig(
            double burstMax, double burstRate,
            double sustainedMax, double sustainedRate,
            double idleGain, double baseCap, double baseDecay,
            double ceilingFactor, double recoveryRate,
            String keyPrefix, long activeStateTtlSeconds,
            long idempotencyTtlSeconds, ClockSource clockSource,
            ColdStartPolicy coldStartPolicy, FailureMode failureMode) {
        this.burstMax = burstMax;
        this.burstRate = burstRate;
        this.sustainedMax = sustainedMax;
        this.sustainedRate = sustainedRate;
        this.idleGain = idleGain;
        this.baseCap = baseCap;
        this.baseDecay = baseDecay;
        this.ceilingFactor = ceilingFactor;
        this.recoveryRate = recoveryRate;
        this.keyPrefix = (keyPrefix == null || keyPrefix.isBlank()) ? "dreb" : keyPrefix;
        this.activeStateTtlSeconds = Math.max(0, activeStateTtlSeconds);
        this.idempotencyTtlSeconds = Math.max(1, idempotencyTtlSeconds);
        this.clockSource = (clockSource == null) ? ClockSource.REDIS_SERVER_TIME : clockSource;
        this.coldStartPolicy = (coldStartPolicy == null) ? ColdStartPolicy.SECURE_CONSERVATIVE : coldStartPolicy;
        this.failureMode = (failureMode == null) ? FailureMode.FAIL_CLOSED : failureMode;
    }

    public static RedisLimiterConfig createDefaultRca70() {
        return new Builder().build();
    }

    public double burstMax() { return burstMax; }
    public double burstRate() { return burstRate; }
    public double sustainedMax() { return sustainedMax; }
    public double sustainedRate() { return sustainedRate; }
    public double idleGain() { return idleGain; }
    public double baseCap() { return baseCap; }
    public double baseDecay() { return baseDecay; }
    public double ceilingFactor() { return ceilingFactor; }
    public double recoveryRate() { return recoveryRate; }
    public String keyPrefix() { return keyPrefix; }
    public long activeStateTtlSeconds() { return activeStateTtlSeconds; }
    public long idempotencyTtlSeconds() { return idempotencyTtlSeconds; }
    public ClockSource clockSource() { return clockSource; }
    public ColdStartPolicy coldStartPolicy() { return coldStartPolicy; }
    public FailureMode failureMode() { return failureMode; }

    public static final class Builder {
        private double burstMax = 700.0;
        private double burstRate = 5000.0 / 3.0; // 1666.6667
        private double sustainedMax = 9000.0 / 7.0; // 1285.7143
        private double sustainedRate = 1000.0;
        private double idleGain = 1500.0;
        private double baseCap = 450.0;
        private double baseDecay = 1.5;
        private double ceilingFactor = 0.75;
        private double recoveryRate = 0.05;
        private String keyPrefix = "dreb";
        private long activeStateTtlSeconds = 0; // Default: durable / no active state expiration
        private long idempotencyTtlSeconds = 60; // Default: 60s idempotency horizon
        private ClockSource clockSource = ClockSource.REDIS_SERVER_TIME;
        private ColdStartPolicy coldStartPolicy = ColdStartPolicy.SECURE_CONSERVATIVE;
        private FailureMode failureMode = FailureMode.FAIL_CLOSED;

        public Builder burstMax(double val) { this.burstMax = val; return this; }
        public Builder burstRate(double val) { this.burstRate = val; return this; }
        public Builder sustainedMax(double val) { this.sustainedMax = val; return this; }
        public Builder sustainedRate(double val) { this.sustainedRate = val; return this; }
        public Builder idleGain(double val) { this.idleGain = val; return this; }
        public Builder baseCap(double val) { this.baseCap = val; return this; }
        public Builder baseDecay(double val) { this.baseDecay = val; return this; }
        public Builder ceilingFactor(double val) { this.ceilingFactor = val; return this; }
        public Builder recoveryRate(double val) { this.recoveryRate = val; return this; }
        public Builder keyPrefix(String val) { this.keyPrefix = val; return this; }
        public Builder activeStateTtlSeconds(long val) { this.activeStateTtlSeconds = val; return this; }
        public Builder idempotencyTtlSeconds(long val) { this.idempotencyTtlSeconds = val; return this; }
        public Builder clockSource(ClockSource val) { this.clockSource = val; return this; }
        public Builder coldStartPolicy(ColdStartPolicy val) { this.coldStartPolicy = val; return this; }
        public Builder failureMode(FailureMode val) { this.failureMode = val; return this; }

        public RedisLimiterConfig build() {
            return new RedisLimiterConfig(
                    burstMax, burstRate, sustainedMax, sustainedRate,
                    idleGain, baseCap, baseDecay, ceilingFactor, recoveryRate,
                    keyPrefix, activeStateTtlSeconds, idempotencyTtlSeconds,
                    clockSource, coldStartPolicy, failureMode
            );
        }
    }
}
