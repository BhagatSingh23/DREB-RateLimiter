package com.dreb.ratelimit.policies;

import com.dreb.ratelimit.Clock;
import com.dreb.ratelimit.ConstantRateRefillStrategy;
import com.dreb.ratelimit.DREBLimiter;
import com.dreb.ratelimit.StateStore;

/**
 * Factory and configuration provider for Rebalanced Capacity Allocation (DREB-RCA)
 * policies developed in Phase 2 to resolve DREB's near-contract over-enforcement defect.
 *
 * <p>DREB-RCA reallocates bucket capacities to eliminate the sub-contract effective
 * accrual bottleneck while strictly preserving Invariants G1, G2, G3 and anti-farming
 * guarantees (Theorem 2).
 */
public final class DrebRcaPolicyFactory {

    private DrebRcaPolicyFactory() {}

    /**
     * Parameter record summarizing the complete mathematical configuration of a DREB-RCA instance.
     */
    public record RcaConfig(
            double nominalBurstC,
            double contractedRateR,
            double alpha,
            double burstMax,
            double burstRate,
            double sustainedMax,
            double sustainedRate,
            double idleCap,
            double idleGain,
            double idleDecay,
            double beta,
            double lambdaRec
    ) {
        public double asymptoticCreditExtractionBound() {
            return idleCap * lambdaRec;
        }

        public double maxPostIdleBurst() {
            return burstMax + idleCap;
        }
    }

    /**
     * Computes the mathematical configuration for a given base capacity fraction {@code alpha}.
     *
     * @param nominalBurstC   target burst volume C
     * @param contractedRateR target sustained contracted rate R
     * @param alpha           base bucket allocation ratio (e.g. 0.70 for 70%)
     * @return fully computed {@link RcaConfig}
     */
    public static RcaConfig computeConfig(double nominalBurstC, double contractedRateR, double alpha) {
        if (nominalBurstC <= 0.0) throw new IllegalArgumentException("nominalBurstC must be > 0: " + nominalBurstC);
        if (contractedRateR <= 0.0) throw new IllegalArgumentException("contractedRateR must be > 0: " + contractedRateR);
        if (alpha <= 0.0 || alpha >= 1.0) throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);

        double burstMax = alpha * nominalBurstC;
        double burstRate = (5.0 / 3.0) * contractedRateR;
        double sustainedMax = (9.0 / 7.0) * nominalBurstC;
        double sustainedRate = contractedRateR;
        double idleCap = 1.5 * (nominalBurstC - burstMax);
        double idleGain = 1.5 * contractedRateR;
        double idleDecay = 1.5;
        double beta = 0.75;
        double lambdaRec = 0.05;

        return new RcaConfig(
                nominalBurstC, contractedRateR, alpha,
                burstMax, burstRate, sustainedMax, sustainedRate,
                idleCap, idleGain, idleDecay, beta, lambdaRec
        );
    }

    /**
     * Computes the recommended DREB-RCA70 configuration (alpha = 0.70).
     */
    public static RcaConfig computeRca70Config(double nominalBurstC, double contractedRateR) {
        return computeConfig(nominalBurstC, contractedRateR, 0.70);
    }

    /**
     * Instantiates a production-ready DREB-RCA limiter.
     */
    public static DREBLimiter createLimiter(StateStore store, Clock clock, RcaConfig cfg) {
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(
                cfg.burstMax(), cfg.burstRate(), cfg.sustainedMax(), cfg.sustainedRate());
        AdaptiveCeilingIdleCreditPolicy idlePolicy = new AdaptiveCeilingIdleCreditPolicy(
                cfg.idleGain(), cfg.idleCap(), cfg.idleDecay(), cfg.beta(), cfg.lambdaRec());
        return new DREBLimiter(store, clock, refill, idlePolicy, cfg.sustainedMax());
    }

    /**
     * Instantiates the recommended DREB-RCA70 limiter (70% base capacity, 30% idle headroom).
     */
    public static DREBLimiter createRca70Limiter(StateStore store, Clock clock, double nominalBurstC, double contractedRateR) {
        RcaConfig cfg = computeRca70Config(nominalBurstC, contractedRateR);
        return createLimiter(store, clock, cfg);
    }
}
