package com.dreb.ratelimit.policies;

import com.dreb.ratelimit.ClientState;
import com.dreb.ratelimit.IdleCreditPolicy;

/**
 * Candidate 2 for Phase 1: Adaptive Idle-Credit Ceiling Policy (DREB-AC).
 *
 * <p>Dynamically scales the maximum idle credit ceiling ({@code effectiveCap})
 * based on a consecutive credit-draw counter stored in {@code state.abuseScore}.
 *
 * <pre>
 *   effectiveCap = baseCap / (1.0 + ceilingSuppressionFactor * abuseScore)
 * </pre>
 *
 * <p><b>Intuition:</b> Repeated burst farming is suppressed directly at the source
 * (the credit ceiling) rather than solely relying on debit inflation. A client bursting
 * repeatedly sees its maximum earnable idle credit contract progressively. A legitimate
 * client that draws credit only after genuine idleness experiences no ceiling contraction.
 */
public final class AdaptiveCeilingIdleCreditPolicy implements IdleCreditPolicy {

    private final double gain;
    private final double baseCap;
    private final double baseDecay;
    private final double ceilingSuppressionFactor;
    private final double recoveryRatePerSecond;

    /**
     * @param gain                     idle accrual rate while burst bucket is full
     * @param baseCap                  nominal maximum idle credit for fresh or fully rested clients
     * @param baseDecay                debit multiplier when credit is drawn (> 1.0)
     * @param ceilingSuppressionFactor sensitivity parameter beta for ceiling contraction (e.g. 0.75)
     * @param recoveryRatePerSecond    linear recovery rate of the draw index per second (e.g. 0.05/s)
     */
    public AdaptiveCeilingIdleCreditPolicy(double gain, double baseCap, double baseDecay,
                                          double ceilingSuppressionFactor, double recoveryRatePerSecond) {
        if (gain < 0.0) throw new IllegalArgumentException("gain must be non-negative: " + gain);
        if (baseCap < 0.0) throw new IllegalArgumentException("baseCap must be non-negative: " + baseCap);
        if (baseDecay <= 1.0) throw new IllegalArgumentException("baseDecay must be > 1.0: " + baseDecay);
        if (ceilingSuppressionFactor < 0.0) throw new IllegalArgumentException("ceilingSuppressionFactor must be non-negative: " + ceilingSuppressionFactor);
        if (recoveryRatePerSecond < 0.0) throw new IllegalArgumentException("recoveryRatePerSecond must be non-negative: " + recoveryRatePerSecond);

        this.gain = gain;
        this.baseCap = baseCap;
        this.baseDecay = baseDecay;
        this.ceilingSuppressionFactor = ceilingSuppressionFactor;
        this.recoveryRatePerSecond = recoveryRatePerSecond;
    }

    @Override
    public void accrue(ClientState state, double dt, double tFull) {
        // Recover draw counter over elapsed time
        if (dt > 0.0 && state.abuseScore > 0.0) {
            state.abuseScore = Math.max(0.0, state.abuseScore - dt * recoveryRatePerSecond);
        }

        double currentCap = computeEffectiveCap(state);
        if (tFull > 0.0) {
            state.idleCredit = Math.min(currentCap, state.idleCredit + tFull * gain);
        } else {
            // In case cap shrank below current credit
            state.idleCredit = Math.min(currentCap, state.idleCredit);
        }
    }

    @Override
    public void spend(ClientState state, double deficit) {
        if (deficit > 0.0) {
            state.idleCredit = Math.max(0.0, state.idleCredit - deficit * baseDecay);
            // Increment consecutive draw counter
            if (baseCap > 0.0) {
                state.abuseScore += (deficit / baseCap);
            }
        }
    }

    @Override
    public double effectiveCredit(ClientState state) {
        return state.idleCredit / baseDecay;
    }

    public double computeEffectiveCap(ClientState state) {
        return baseCap / (1.0 + ceilingSuppressionFactor * state.abuseScore);
    }

    public double gain() { return gain; }
    public double baseCap() { return baseCap; }
    public double baseDecay() { return baseDecay; }
    public double ceilingSuppressionFactor() { return ceilingSuppressionFactor; }
    public double recoveryRatePerSecond() { return recoveryRatePerSecond; }
}
