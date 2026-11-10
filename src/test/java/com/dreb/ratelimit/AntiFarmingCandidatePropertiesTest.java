package com.dreb.ratelimit;

import com.dreb.ratelimit.policies.AdaptiveCeilingIdleCreditPolicy;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based mathematical invariant tests for Phase 1 Anti-Farming Candidates.
 */
public class AntiFarmingCandidatePropertiesTest {

    @Property(tries = 1000)
    void stateInvariantsHoldForAdaptiveCeiling(@ForAll("randomTraces") List<TraceStep> trace) {
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();

        double bMax = 30.0;
        double bRate = 50.0;
        double sMax = 90.0;
        double sRate = 70.0;
        double idleCap = 60.0;
        double idleGain = 40.0;

        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(bMax, bRate, sMax, sRate);
        AdaptiveCeilingIdleCreditPolicy policy = new AdaptiveCeilingIdleCreditPolicy(
                idleGain, idleCap, 1.5, 0.75, 0.05);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, policy, sMax);

        double t = 0.0;
        for (TraceStep step : trace) {
            t += step.dt();
            clock.set(t);
            limiter.allow("prop_client", step.cost());

            ClientState s = limiter.peekState("prop_client");
            assertTrue(s.burstTokens >= -1e-9 && s.burstTokens <= bMax + 1e-9,
                    "burstTokens out of bounds: " + s.burstTokens);
            assertTrue(s.sustainedTokens >= -1e-9 && s.sustainedTokens <= sMax + 1e-9,
                    "sustainedTokens out of bounds: " + s.sustainedTokens);
            assertTrue(s.idleCredit >= -1e-9 && s.idleCredit <= idleCap + 1e-9,
                    "idleCredit out of bounds: " + s.idleCredit);
            assertTrue(s.abuseScore >= -1e-9,
                    "abuseScore must be non-negative: " + s.abuseScore);
        }
    }

    @Property(tries = 1000)
    void cumulativeAdmissionBoundHoldsForAdaptiveCeiling(@ForAll("randomTraces") List<TraceStep> trace) {
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();

        double bMax = 20.0;
        double bRate = 40.0;
        double sMax = 60.0;
        double sRate = 50.0;
        double idleCap = 40.0;
        double idleGain = 30.0;

        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(bMax, bRate, sMax, sRate);
        AdaptiveCeilingIdleCreditPolicy policy = new AdaptiveCeilingIdleCreditPolicy(
                idleGain, idleCap, 1.5, 0.75, 0.05);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, policy, sMax);

        double t = 0.0;
        long totalAdmittedCost = 0;

        for (TraceStep step : trace) {
            t += step.dt();
            clock.set(t);
            if (limiter.allow("prop_client", step.cost()) == Decision.ALLOW) {
                totalAdmittedCost += step.cost();
            }

            // Cumulative bound Theorem 1: Total admitted cost in time T <= S_max + S_rate * T
            double maxAllowedEnvelope = sMax + sRate * t + 1e-9;
            assertTrue(totalAdmittedCost <= maxAllowedEnvelope,
                    String.format("Cumulative admission bound violated at t=%.4f: admitted=%d, bound=%.2f",
                            t, totalAdmittedCost, maxAllowedEnvelope));
        }
    }

    @Provide
    Arbitrary<List<TraceStep>> randomTraces() {
        Arbitrary<Double> dtArb = Arbitraries.doubles().between(0.0, 10.0);
        Arbitrary<Integer> costArb = Arbitraries.integers().between(1, 20);
        Arbitrary<TraceStep> stepArb = Combinators.combine(dtArb, costArb).as(TraceStep::new);
        return stepArb.list().ofMinSize(10).ofMaxSize(100);
    }

    public record TraceStep(double dt, int cost) {}
}
