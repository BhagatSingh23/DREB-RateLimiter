package com.dreb.ratelimit;

/**
 * Entry point for constructing a DREB rate limiter with sane defaults. This is the class
 * most users should start with — {@link DREBLimiter} and {@link DREBLimiterV2} are the
 * underlying implementations and remain available directly for anyone who wants full
 * control over every parameter, but {@code Dreb} handles the parameter-mapping math for you.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // A rate limiter allowing roughly 100 requests/sec sustained, per client.
 * RateLimiter limiter = Dreb.builder()
 *         .capacity(100)
 *         .refillRate(100)
 *         .build();
 *
 * if (limiter.allow(userId, 1) == Decision.ALLOW) {
 *     // serve the request
 * } else {
 *     // return HTTP 429
 * }
 * }</pre>
 *
 * <h2>Choosing a mapping</h2>
 * DREB divides a client's total capacity into a fast burst bucket, a slow sustained bucket,
 * and an idle-earned credit pool. There is more than one reasonable way to size those pieces
 * from a single "capacity" + "refill rate" pair, and which one is "correct" depends on what
 * you're optimizing for:
 * <ul>
 *   <li>{@link Mapping#PROPORTIONAL} (default) — preserves the reference implementation's
 *       internal ratios exactly. More conservative on instantaneous bursts, more restrictive
 *       on sustained abuse. Use this if resisting repeated/sustained abuse matters more to you
 *       than maximizing burst tolerance.</li>
 *   <li>{@link Mapping#BURST_CEILING_MATCHED} — sizes the burst+idle-credit pool so a client's
 *       maximum reachable instantaneous burst matches a plain fixed-capacity bucket's ceiling.
 *       Use this if you want DREB's idle-reward behavior without reducing burst tolerance
 *       relative to a simple token bucket at the same capacity.</li>
 * </ul>
 * Both are described in full, with the exact derivation, in the accompanying paper's Section 4
 * and the benchmark repository's {@code docs/algorithms.md}.
 *
 * <h2>v1 vs v2</h2>
 * {@link #builder()} constructs v1 ({@link DREBLimiter} + {@link LinearIdleCreditPolicy}), which
 * penalizes every draw on idle credit at the same flat rate. {@link #builderV2()} constructs v2
 * ({@link DREBLimiterV2} + {@link AdaptiveIdleCreditPolicy}), which tracks a client's recent
 * abuse pattern and only escalates the penalty for clients that repeatedly draw on idle credit —
 * a first-time legitimate burst after genuine idleness costs close to nothing extra, while a
 * repeat abuser is charged progressively more, including an abuse-adaptive surcharge on the
 * sustained bucket itself. v2 is newer and, in the paper's own benchmarking, outperforms v1 in
 * most (not all) tested scenarios — see the paper's evaluation section for the specific
 * scenario where v1's {@code PROPORTIONAL} mapping edges out v2. If you're not sure which to
 * use, start with v2.
 */
public final class Dreb {

    private Dreb() {
    }

    /** Starts building a v1 (flat idle-decay) rate limiter. */
    public static Builder builder() {
        return new Builder();
    }

    /** Starts building a v2 (abuse-adaptive) rate limiter. */
    public static BuilderV2 builderV2() {
        return new BuilderV2();
    }

    /** Which capacity-to-parameter mapping to use. See the class Javadoc above. */
    public enum Mapping {
        PROPORTIONAL, BURST_CEILING_MATCHED
    }

    /** Builder for a v1 {@link DREBLimiter}. */
    public static final class Builder {
        private double capacity = -1;
        private double refillRate = -1;
        private Mapping mapping = Mapping.PROPORTIONAL;
        private double idleDecay = 1.5;
        private Clock clock = new SystemClock();
        private StateStore store = new InMemoryStateStore();

        private Builder() {
        }

        /** Required. The total capacity a client is allowed, matching what a plain fixed-capacity bucket would use. */
        public Builder capacity(double capacity) {
            this.capacity = capacity;
            return this;
        }

        /** Required. The sustained (long-run average) rate, in requests/second. */
        public Builder refillRate(double refillRate) {
            this.refillRate = refillRate;
            return this;
        }

        /** Optional. Defaults to {@link Mapping#PROPORTIONAL}. */
        public Builder mapping(Mapping mapping) {
            this.mapping = mapping;
            return this;
        }

        /** Optional. Defaults to 1.5 (the paper's reference value). Must be &gt; 1.0 for the idle-reward mechanism to be self-limiting. */
        public Builder idleDecay(double idleDecay) {
            this.idleDecay = idleDecay;
            return this;
        }

        /** Optional. Defaults to {@link SystemClock}. Supply a {@link ManualClock} for deterministic tests. */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /** Optional. Defaults to {@link InMemoryStateStore} (single JVM instance). Supply a {@link RedisStateStore} for a distributed deployment. */
        public Builder store(StateStore store) {
            this.store = store;
            return this;
        }

        public RateLimiter build() {
            requireConfigured(capacity, refillRate);
            Params p = Params.derive(capacity, refillRate, mapping);
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(
                    p.burstMax, p.burstRate, p.sustainedMax, refillRate);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(p.idleGain, p.idleCap, idleDecay);
            return new DREBLimiter(store, clock, refill, idle, p.sustainedMax);
        }
    }

    /** Builder for a v2 {@link DREBLimiterV2}. */
    public static final class BuilderV2 {
        private double capacity = -1;
        private double refillRate = -1;
        private Mapping mapping = Mapping.BURST_CEILING_MATCHED;
        private double baseDecay = 1.0;
        private double abuseGain = 0.3;
        private double abuseIncrement = 2.0;
        private double abuseDecayRate = 1.5;
        private double sustainedCostGain = 0.35;
        private Clock clock = new SystemClock();
        private StateStore store = new InMemoryStateStore();

        private BuilderV2() {
        }

        public BuilderV2 capacity(double capacity) {
            this.capacity = capacity;
            return this;
        }

        public BuilderV2 refillRate(double refillRate) {
            this.refillRate = refillRate;
            return this;
        }

        /** Optional. Defaults to {@link Mapping#BURST_CEILING_MATCHED} (what v2 was tuned against). */
        public BuilderV2 mapping(Mapping mapping) {
            this.mapping = mapping;
            return this;
        }

        /** Optional. See {@link AdaptiveIdleCreditPolicy} for what each of these five tuning parameters does. Defaults are the paper's evaluated values. */
        public BuilderV2 tuning(double baseDecay, double abuseGain, double abuseIncrement,
                                 double abuseDecayRate, double sustainedCostGain) {
            this.baseDecay = baseDecay;
            this.abuseGain = abuseGain;
            this.abuseIncrement = abuseIncrement;
            this.abuseDecayRate = abuseDecayRate;
            this.sustainedCostGain = sustainedCostGain;
            return this;
        }

        public BuilderV2 clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public BuilderV2 store(StateStore store) {
            this.store = store;
            return this;
        }

        public RateLimiter build() {
            requireConfigured(capacity, refillRate);
            Params p = Params.derive(capacity, refillRate, mapping);
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(
                    p.burstMax, p.burstRate, p.sustainedMax, refillRate);
            AdaptiveIdleCreditPolicy idle = new AdaptiveIdleCreditPolicy(
                    p.idleGain, p.idleCap, baseDecay, abuseGain, abuseIncrement, abuseDecayRate);
            return new DREBLimiterV2(store, clock, refill, idle, p.sustainedMax, sustainedCostGain);
        }
    }

    private static void requireConfigured(double capacity, double refillRate) {
        if (capacity <= 0) {
            throw new IllegalStateException("capacity(...) must be set to a positive value before build()");
        }
        if (refillRate <= 0) {
            throw new IllegalStateException("refillRate(...) must be set to a positive value before build()");
        }
    }

    /** Derived burst/sustained/idle parameters for a given capacity, refillRate, and mapping. */
    private static final class Params {
        final double burstMax, burstRate, sustainedMax, idleGain, idleCap;

        private Params(double burstMax, double burstRate, double sustainedMax, double idleGain, double idleCap) {
            this.burstMax = burstMax;
            this.burstRate = burstRate;
            this.sustainedMax = sustainedMax;
            this.idleGain = idleGain;
            this.idleCap = idleCap;
        }

        static Params derive(double capacity, double refillRate, Mapping mapping) {
            double burstMax, sustainedMax;
            if (mapping == Mapping.PROPORTIONAL) {
                // Paper ratio: burstMax = 20/60 of sustainedMax = capacity.
                sustainedMax = capacity;
                burstMax = sustainedMax / 3.0;
            } else {
                // burst_ceiling_matched: burstMax + idleCap/idleDecay == capacity,
                // preserving idleCap = 2*burstMax and sustainedMax = 3*burstMax.
                burstMax = capacity * 3.0 / 7.0;
                sustainedMax = burstMax * 3.0;
            }
            double idleCap = burstMax * 2.0;
            double burstRate = refillRate * 5.0 / 3.0;
            double idleGain = refillRate * 2.0 / 3.0;
            return new Params(burstMax, burstRate, sustainedMax, idleGain, idleCap);
        }
    }
}
