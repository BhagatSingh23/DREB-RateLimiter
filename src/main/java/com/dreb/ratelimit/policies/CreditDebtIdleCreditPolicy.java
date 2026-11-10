package com.dreb.ratelimit.policies;

import com.dreb.ratelimit.ClientState;
import com.dreb.ratelimit.IdleCreditPolicy;

/**
 * Candidate 3 for Phase 1: Credit Debt and Asymmetric Cooldown Policy (DREB-CD).
 *
 * <p>Tracks outstanding credit debt in {@code state.abuseScore}.
 * When idle credit is drawn to cover a deficit, an equivalent quantity of debt is incurred.
 * During subsequent idle periods, newly accrued gross credit must first pay down
 * the outstanding debt before any usable idle credit accumulates in the burst pool.
 *
 * <pre>
 *   grossAccrual = tFull * gain
 *   repay = min(debt, grossAccrual)
 *   debt = debt - repay
 *   netAccrual = grossAccrual - repay
 *   idleCredit = min(cap, idleCredit + netAccrual)
 * </pre>
 *
 * <p><b>Intuition:</b> Repeated credit farming is made uneconomical because drawing credit
 * creates a cooldown obligation. An adversary attempting to farm credit must wait double
 * the time to regain full credit capacity.
 */
public final class CreditDebtIdleCreditPolicy implements IdleCreditPolicy {

    private final double gain;
    private final double cap;
    private final double baseDecay;
    private final double debtRecoveryRate; // Additional passive debt dissipation per second

    /**
     * @param gain             idle accrual rate while burst bucket is full
     * @param cap              maximum idle credit
     * @param baseDecay        debit multiplier when credit is drawn (> 1.0)
     * @param debtRecoveryRate passive debt dissipation per second of elapsed time
     */
    public CreditDebtIdleCreditPolicy(double gain, double cap, double baseDecay, double debtRecoveryRate) {
        if (gain < 0.0) throw new IllegalArgumentException("gain must be non-negative: " + gain);
        if (cap < 0.0) throw new IllegalArgumentException("cap must be non-negative: " + cap);
        if (baseDecay <= 1.0) throw new IllegalArgumentException("baseDecay must be > 1.0: " + baseDecay);
        if (debtRecoveryRate < 0.0) throw new IllegalArgumentException("debtRecoveryRate must be non-negative: " + debtRecoveryRate);

        this.gain = gain;
        this.cap = cap;
        this.baseDecay = baseDecay;
        this.debtRecoveryRate = debtRecoveryRate;
    }

    public CreditDebtIdleCreditPolicy(double gain, double cap, double baseDecay) {
        this(gain, cap, baseDecay, 0.0);
    }

    @Override
    public void accrue(ClientState state, double dt, double tFull) {
        // Passive debt dissipation
        if (dt > 0.0 && debtRecoveryRate > 0.0 && state.abuseScore > 0.0) {
            state.abuseScore = Math.max(0.0, state.abuseScore - dt * debtRecoveryRate);
        }

        if (tFull > 0.0) {
            double grossCredit = tFull * gain;
            double outstandingDebt = state.abuseScore;

            if (outstandingDebt > 0.0) {
                double repaid = Math.min(outstandingDebt, grossCredit);
                state.abuseScore = outstandingDebt - repaid;
                double netCredit = grossCredit - repaid;
                if (netCredit > 0.0) {
                    state.idleCredit = Math.min(cap, state.idleCredit + netCredit);
                }
            } else {
                state.idleCredit = Math.min(cap, state.idleCredit + grossCredit);
            }
        }
    }

    @Override
    public void spend(ClientState state, double deficit) {
        if (deficit > 0.0) {
            double debit = deficit * baseDecay;
            state.idleCredit = Math.max(0.0, state.idleCredit - debit);
            // Incur credit debt
            state.abuseScore = Math.min(cap, state.abuseScore + debit);
        }
    }

    @Override
    public double effectiveCredit(ClientState state) {
        return state.idleCredit / baseDecay;
    }

    public double gain() { return gain; }
    public double cap() { return cap; }
    public double baseDecay() { return baseDecay; }
    public double debtRecoveryRate() { return debtRecoveryRate; }
}
