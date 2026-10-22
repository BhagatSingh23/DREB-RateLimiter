package com.dreb.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdleCreditOverAccrualReproductionTest {

    @Test
    @DisplayName("P1 reproduction: empty burst bucket refilling mid-gap should accrue credit only for time full")
    void testMidGapFillIdleCreditAccrual() {
        // burstMax=10, burstRate=10/s, sustainedMax=50, sustainedRate=10/s
        // idleGain=5/s, idleCap=20, idleDecay=1.5
        ManualClock clock = new ManualClock(0.0);
        InMemoryStateStore store = new InMemoryStateStore();
        ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(10, 10, 50, 10);
        LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(5, 20, 1.5);
        DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50);

        // Client starts fresh at t=0: burst=10, sustained=50, idleCredit=0
        // Drain all 10 burst tokens at t=0
        for (int i = 0; i < 10; i++) {
            assertEquals(Decision.ALLOW, limiter.allow("c1", 1));
        }
        ClientState stateAfterDrain = limiter.peekState("c1");
        assertEquals(0.0, stateAfterDrain.burstTokens, 1e-6, "Burst tokens should be drained to 0");
        assertEquals(0.0, stateAfterDrain.idleCredit, 1e-6, "Idle credit should be 0");

        // Advance 2 seconds.
        // In 2 seconds: burst bucket refills from 0 to 10.
        // It takes (10 - 0)/10 = 1.0 second to fill.
        // The bucket was only full for 2.0 - 1.0 = 1.0 second.
        // So idle credit accrued should be 1.0s * idleGain(5) = 5.0.
        // (The buggy code credited 2.0s * 5 = 10.0 because it refilled first, saw burst was full, and credited whole dt=2s!)
        clock.set(2.0);
        limiter.allow("c1", 1);

        ClientState stateAfterRefill = limiter.peekState("c1");
        // We expect idle credit to be 5.0 (accrued for 1.0s). If buggy, it would be 10.0!
        assertEquals(5.0, stateAfterRefill.idleCredit, 1e-6,
                "Idle credit should only accrue for the 1.0s the bucket was actually full, not the full 2.0s dt");
    }
}
