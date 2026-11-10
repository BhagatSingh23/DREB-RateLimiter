package com.dreb.ratelimit;

import com.dreb.ratelimit.policies.DrebRcaPolicyFactory;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property-based mathematical invariant and security bound tests for DREB-RCA70.
 * Verifies Invariants G1, G2, G3, Theorem 2 bound, and post-idle burst guarantee.
 */
public class DrebRcaPropertiesTest {

    private static final double C = 1000.0;
    private static final double R = 1000.0;

    public record TraceStep(double dt, int cost) {}

    @Provide
    Arbitrary<List<TraceStep>> randomTraces() {
        Arbitrary<Double> dt = Arbitraries.doubles().between(0.0, 2.0);
        Arbitrary<Integer> cost = Arbitraries.integers().between(1, 1200);
        Arbitrary<TraceStep> step = Combinators.combine(dt, cost).as(TraceStep::new);
        return step.list().ofMinSize(10).ofMaxSize(100);
    }

    @Property(tries = 1000)
    void cumulativeAdmissionBoundG1Holds(@ForAll("randomTraces") List<TraceStep> trace) {
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();

        DrebRcaPolicyFactory.RcaConfig cfg = DrebRcaPolicyFactory.computeRca70Config(C, R);
        DREBLimiter limiter = DrebRcaPolicyFactory.createLimiter(store, clock, cfg);

        double totalAdmitted = 0.0;
        double t = 0.0;

        for (TraceStep step : trace) {
            t += step.dt();
            clock.set(t);
            Decision d = limiter.allow("client_g1", step.cost());
            if (d == Decision.ALLOW) {
                totalAdmitted += step.cost();
            }

            double maxAllowedVolume = cfg.sustainedMax() + cfg.sustainedRate() * t + 1e-6;
            assertTrue(totalAdmitted <= maxAllowedVolume,
                    String.format("Invariant G1 violated! Admitted %.2f > Max %.2f at t=%.4f",
                            totalAdmitted, maxAllowedVolume, t));
        }
    }

    @Property(tries = 1000)
    void stateBoundsHold(@ForAll("randomTraces") List<TraceStep> trace) {
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();

        DrebRcaPolicyFactory.RcaConfig cfg = DrebRcaPolicyFactory.computeRca70Config(C, R);
        DREBLimiter limiter = DrebRcaPolicyFactory.createLimiter(store, clock, cfg);

        double t = 0.0;
        for (TraceStep step : trace) {
            t += step.dt();
            clock.set(t);
            limiter.allow("client_state", step.cost());

            ClientState s = limiter.peekState("client_state");
            assertTrue(s.burstTokens >= -1e-9 && s.burstTokens <= cfg.burstMax() + 1e-9,
                    "Burst bucket out of bounds: " + s.burstTokens);
            assertTrue(s.sustainedTokens >= -1e-9 && s.sustainedTokens <= cfg.sustainedMax() + 1e-9,
                    "Sustained bucket out of bounds: " + s.sustainedTokens);
            assertTrue(s.idleCredit >= -1e-9 && s.idleCredit <= cfg.idleCap() + 1e-9,
                    "Idle credit out of bounds: " + s.idleCredit);
            assertTrue(s.abuseScore >= -1e-9,
                    "Abuse score must be non-negative: " + s.abuseScore);
        }
    }

    @Property(tries = 1000)
    void theorem2FiniteHorizonBoundHolds(@ForAll("randomTraces") List<TraceStep> trace) {
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();

        DrebRcaPolicyFactory.RcaConfig cfg = DrebRcaPolicyFactory.computeRca70Config(C, R);
        DREBLimiter limiter = DrebRcaPolicyFactory.createLimiter(store, clock, cfg);

        double totalCreditDrawn = 0.0;
        double t = 0.0;

        for (TraceStep step : trace) {
            t += step.dt();
            clock.set(t);

            ClientState before = limiter.peekState("client_th2");
            double creditBefore = (before == null) ? 0.0 : before.idleCredit;

            Decision d = limiter.allow("client_th2", step.cost());

            ClientState after = limiter.peekState("client_th2");
            double creditAfter = after.idleCredit;

            if (d == Decision.ALLOW) {
                double deficit = step.cost() - ((before == null) ? cfg.burstMax() : Math.min(cfg.burstMax(), before.burstTokens + step.dt() * cfg.burstRate()));
                if (deficit > 0) {
                    totalCreditDrawn += deficit;
                }
            }

            // Finite horizon Theorem 2 check:
            // Sum(creditDrawn) <= idleCap * (lambda_rec * t + K(t))
            double bound = cfg.idleCap() * (cfg.lambdaRec() * t + after.abuseScore) + 1e-6;
            assertTrue(totalCreditDrawn <= bound,
                    String.format("Theorem 2 bound violated! Total credit drawn %.2f > Bound %.2f at t=%.4f (K=%.4f)",
                            totalCreditDrawn, bound, t, after.abuseScore));
        }
    }

    @Property(tries = 1)
    void fullPostIdleBurstAdmitted() {
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();

        DREBLimiter limiter = DrebRcaPolicyFactory.createRca70Limiter(store, clock, C, R);

        // At t=0, fresh client has B=700 (bursts > 700 require demonstrated idle time)
        Decision d0 = limiter.allow("client_idle", 700);
        assertEquals(Decision.ALLOW, d0);

        // Rest for 2 seconds (sufficient to refill burst bucket and earn idle credit)
        clock.set(2.0);

        // Full burst of nominal capacity C = 1000
        Decision d1 = limiter.allow("client_idle", (int) C);
        assertEquals(Decision.ALLOW, d1, "Post-idle burst of size C=1000 must be admitted!");

        ClientState s = limiter.peekState("client_idle");
        // Deficit was C - B_max = 1000 - 700 = 300 tokens
        // Idle credit spent: 300 * 1.5 = 450 tokens (exact idleCap)
        assertEquals(0.0, s.burstTokens, 1e-6);
        assertEquals(0.0, s.idleCredit, 1e-6);
        // Abuse score increased by deficit / idleCap = 300 / 450 = 0.6667
        assertEquals(300.0 / 450.0, s.abuseScore, 1e-6);
    }

    public static double computeMinIdleDuration(double B0, double S0, double I0, double K0, int cost) {
        double bMax = 700.0;
        double bRate = 5000.0 / 3.0;
        double sMax = 9000.0 / 7.0;
        double sRate = 1000.0;
        double baseCap = 450.0;
        double baseDecay = 1.5;
        double gain = 1500.0;
        double beta = 0.75;
        double lambdaRec = 0.05;

        if (cost > Math.min(sMax, bMax + baseCap / baseDecay)) {
            return Double.POSITIVE_INFINITY;
        }

        double tauS = Math.max(0.0, (cost - S0) / sRate);

        if (cost <= bMax) {
            double neededB = Math.max(0.0, cost - I0 / baseDecay);
            double tauB = Math.max(0.0, (neededB - B0) / bRate);
            return Math.max(tauS, tauB);
        }

        double d = cost - bMax;
        double iReq = baseDecay * d;
        double kTarget = Math.max(0.0, (baseCap / iReq - 1.0) / beta);
        double tauK = Math.max(0.0, (K0 - kTarget) / lambdaRec);

        double tFill = Math.max(0.0, (bMax - B0) / bRate);
        double tauCredit = tFill + Math.max(0.0, (iReq - I0) / gain);

        return Math.max(tauS, Math.max(tauK, tauCredit));
    }

    @Property(tries = 1)
    void verifyAllTenEdgeCases() {
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();
        DREBLimiter limiter = DrebRcaPolicyFactory.createRca70Limiter(store, clock, C, R);

        record TestCase(String name, double b0, double s0, double i0, double k0, int cost, double expectedTau) {}
        List<TestCase> cases = List.of(
                new TestCase("Case 1 (Reference RCA70)", 0.0, 280.0, 0.0, 0.0, 1000, 0.720),
                new TestCase("Case 2 (Depleted S)", 0.0, 0.0, 0.0, 0.0, 1000, 1.000),
                new TestCase("Case 3 (K0 = 0.5)", 0.0, 280.0, 0.0, 0.5, 1000, 10.000),
                new TestCase("Case 4 (I0 = 225)", 0.0, 280.0, 225.0, 0.0, 1000, 0.720),
                new TestCase("Case 5 (B0 = Bmax)", 700.0, 1000.0, 0.0, 0.0, 1000, 0.300),
                new TestCase("Case 6 (S0 = Smax)", 0.0, 1285.71, 0.0, 0.0, 1000, 0.720),
                new TestCase("Case 7 (c = Bmax)", 0.0, 700.0, 0.0, 0.0, 700, 0.420),
                new TestCase("Case 8 (c = 850)", 0.0, 850.0, 0.0, 0.0, 850, 0.570),
                new TestCase("Case 9 (c = 850, K0=0.5)", 0.0, 850.0, 0.0, 0.5, 850, 0.570),
                new TestCase("Case 10 (c = 1100)", 700.0, 1285.71, 450.0, 0.0, 1100, Double.POSITIVE_INFINITY)
        );

        for (TestCase tc : cases) {
            double calculated = computeMinIdleDuration(tc.b0, tc.s0, tc.i0, tc.k0, tc.cost);
            if (Double.isInfinite(tc.expectedTau)) {
                assertTrue(Double.isInfinite(calculated), tc.name + " should be infinite");
                continue;
            }
            assertEquals(tc.expectedTau, calculated, 1e-4, tc.name + " formula calculation mismatch");

            // Verify in limiter:
            // At tau - 0.005s: must DENY
            if (tc.expectedTau > 0.010) {
                ClientState sDeny = ClientState.fresh(700.0, 1285.71, 0.0);
                sDeny.burstTokens = tc.b0;
                sDeny.sustainedTokens = tc.s0;
                sDeny.idleCredit = tc.i0;
                sDeny.abuseScore = tc.k0;
                sDeny.lastRefillTs = 0.0;
                store.save("client_deny_" + tc.name, sDeny);

                clock.set(tc.expectedTau - 0.005);
                Decision dDeny = limiter.allow("client_deny_" + tc.name, tc.cost);
                assertEquals(Decision.DENY, dDeny, tc.name + " must DENY before min idle duration");
            }

            // At tau: must ALLOW
            ClientState sAllow = ClientState.fresh(700.0, 1285.71, 0.0);
            sAllow.burstTokens = tc.b0;
            sAllow.sustainedTokens = tc.s0;
            sAllow.idleCredit = tc.i0;
            sAllow.abuseScore = tc.k0;
            sAllow.lastRefillTs = 0.0;
            store.save("client_allow_" + tc.name, sAllow);

            clock.set(tc.expectedTau);
            Decision dAllow = limiter.allow("client_allow_" + tc.name, tc.cost);
            assertEquals(Decision.ALLOW, dAllow, tc.name + " must ALLOW at min idle duration");
        }
    }
}
