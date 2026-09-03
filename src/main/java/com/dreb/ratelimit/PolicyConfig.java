package com.dreb.ratelimit;

/**
 * Declarative configuration for constructing a {@link RateLimiter} via
 * {@link RateLimiterFactory}. Immutable; build with {@link Builder}.
 */
public final class PolicyConfig {

    public enum Algorithm { TOKEN_BUCKET, DREB }

    public final Algorithm algorithm;
    public final double burstMax;
    public final double burstRate;
    public final double sustainedMax;
    public final double sustainedRate;
    public final double idleGain;
    public final double idleCap;
    public final double idleDecay;
    public final boolean useRedis;

    private PolicyConfig(Builder b) {
        this.algorithm = b.algorithm;
        this.burstMax = b.burstMax;
        this.burstRate = b.burstRate;
        this.sustainedMax = b.sustainedMax;
        this.sustainedRate = b.sustainedRate;
        this.idleGain = b.idleGain;
        this.idleCap = b.idleCap;
        this.idleDecay = b.idleDecay;
        this.useRedis = b.useRedis;
    }

    public static Builder builder(Algorithm algorithm) {
        return new Builder(algorithm);
    }

    public static final class Builder {
        private final Algorithm algorithm;
        private double burstMax = 20;
        private double burstRate = 5;
        private double sustainedMax = 60;
        private double sustainedRate = 3;
        private double idleGain = 2.0;
        private double idleCap = 40;
        private double idleDecay = 1.5;
        private boolean useRedis = false;

        private Builder(Algorithm algorithm) {
            this.algorithm = algorithm;
        }

        public Builder burstMax(double v) { this.burstMax = v; return this; }
        public Builder burstRate(double v) { this.burstRate = v; return this; }
        public Builder sustainedMax(double v) { this.sustainedMax = v; return this; }
        public Builder sustainedRate(double v) { this.sustainedRate = v; return this; }
        public Builder idleGain(double v) { this.idleGain = v; return this; }
        public Builder idleCap(double v) { this.idleCap = v; return this; }
        public Builder idleDecay(double v) { this.idleDecay = v; return this; }
        public Builder useRedis(boolean v) { this.useRedis = v; return this; }

        public PolicyConfig build() {
            return new PolicyConfig(this);
        }
    }
}
