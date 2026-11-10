package com.dreb.ratelimit.policies;

import com.dreb.ratelimit.ClientState;
import com.dreb.ratelimit.IdleCreditPolicy;

/**
 * Candidate 1 for Phase 1: Exponential-Memory Adaptive Idle-Credit Policy (DREB-EMA).
 *
 * <p>Replaces linear abuse score dissipation ({@code state.abuseScore - dt * rate})
 * with exponential half-life decay:
 * <pre>
 *   abuseScore(t + dt) = abuseScore(t) * 2^(-dt / halfLifeSeconds)
 * </pre>
 *
 * <p><b>Intuition:</b> Periodic credit-farming adversaries deliberately idle for short periods
 * (e.g. 1-3 seconds) to accrue idle credit and burst repeatedly. Under linear decay, an idle gap
 * of 2 seconds wipes out up to 3.0 units of abuse score, completely blinding DREB v2.
 * Under exponential decay with an appropriate half-life (e.g. 20-30 seconds), an adversary
 * waiting 2.3 seconds retains ~95% of its accumulated abuse penalty. Consecutive bursts
 * drive the abuse score higher, escalating the effective decay multiplier and suppressing
 * admitted burst size.
 *
 * <p>Conversely, a legitimate client that rests for 2-5 minutes experiences 6-15 half-lives,
 * reducing its abuse score to zero and preserving 100% of its legitimate idle reward.
 */
public final class ExponentialAdaptiveIdleCreditPolicy implements IdleCreditPolicy {

    private final double gain;
    private final double cap;
    private final double baseDecay;
    private final double abuseGain;
    private final double abuseIncrement;
    private final double halfLifeSeconds;
    private final double decayFactorPerSecond;

    /**
     * @param gain            credit accrual rate while burst bucket is full
     * @param cap             maximum idle credit
     * @param baseDecay       base debit multiplier when credit is drawn (> 1.0)
     * @param abuseGain       scaling factor applied to abuseScore in effective decay
     * @param abuseIncrement  increment added to abuseScore per normalized unit of credit drawn
     * @param halfLifeSeconds half-life of abuse score in seconds (e.g. 20.0s)
     */
    public ExponentialAdaptiveIdleCreditPolicy(double gain, double cap, double baseDecay,
                                               double abuseGain, double abuseIncrement,
                                               double halfLifeSeconds) {
        if (gain < 0.0) throw new IllegalArgumentException("gain must be non-negative: " + gain);
        if (cap < 0.0) throw new IllegalArgumentException("cap must be non-negative: " + cap);
        if (baseDecay <= 1.0) throw new IllegalArgumentException("baseDecay must be > 1.0: " + baseDecay);
        if (abuseGain < 0.0) throw new IllegalArgumentException("abuseGain must be non-negative: " + abuseGain);
        if (abuseIncrement < 0.0) throw new IllegalArgumentException("abuseIncrement must be non-negative: " + abuseIncrement);
        if (halfLifeSeconds <= 0.0) throw new IllegalArgumentException("halfLifeSeconds must be positive: " + halfLifeSeconds);

        this.gain = gain;
        this.cap = cap;
        this.baseDecay = baseDecay;
        this.abuseGain = abuseGain;
        this.abuseIncrement = abuseIncrement;
        this.halfLifeSeconds = halfLifeSeconds;
        // Pre-compute ln(2) / halfLifeSeconds
        this.decayFactorPerSecond = Math.log(2.0) / halfLifeSeconds;
    }

    @Override
    public void accrue(ClientState state, double dt, double tFull) {
        if (tFull > 0.0) {
            state.idleCredit = Math.min(cap, state.idleCredit + tFull * gain);
        }
        if (dt > 0.0 && state.abuseScore > 0.0) {
            // Exponential half-life decay: a * exp(-dt * ln(2) / T_half)
            state.abuseScore = state.abuseScore * Math.exp(-dt * decayFactorPerSecond);
            if (state.abuseScore < 1e-9) {
                state.abuseScore = 0.0;
            }
        }
    }

    @Override
    public void spend(ClientState state, double deficit) {
        if (deficit > 0.0) {
            double effectiveDecay = computeEffectiveDecay(state);
            state.idleCredit = Math.max(0.0, state.idleCredit - deficit * effectiveDecay);
            if (cap > 0.0) {
                state.abuseScore += abuseIncrement * (deficit / cap);
            }
        }
    }

    @Override
    public double effectiveCredit(ClientState state) {
        double effectiveDecay = computeEffectiveDecay(state);
        return effectiveDecay > 0.0 ? (state.idleCredit / effectiveDecay) : state.idleCredit;
    }

    public double computeEffectiveDecay(ClientState state) {
        return baseDecay + abuseGain * state.abuseScore;
    }

    public double gain() { return gain; }
    public double cap() { return cap; }
    public double baseDecay() { return baseDecay; }
    public double abuseGain() { return abuseGain; }
    public double abuseIncrement() { return abuseIncrement; }
    public double halfLifeSeconds() { return halfLifeSeconds; }
}
