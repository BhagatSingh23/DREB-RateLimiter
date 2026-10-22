package com.dreb.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrencyContentionReproductionTest {

    @Test
    @DisplayName("P2 reproduction: concurrent calls to allow on the same client must not over-admit beyond capacity")
    void testConcurrentContentionEnforcement() throws InterruptedException {
        // Run several trials to catch race condition
        for (int trial = 0; trial < 10; trial++) {
            ManualClock clock = new ManualClock(0.0);
            InMemoryStateStore store = new InMemoryStateStore();
            // Capacity: burstMax=20, sustainedMax=50
            ConstantRateRefillStrategy refill = new ConstantRateRefillStrategy(20, 0, 50, 0);
            LinearIdleCreditPolicy idle = new LinearIdleCreditPolicy(0, 0, 1.5);
            DREBLimiter limiter = new DREBLimiter(store, clock, refill, idle, 50);

            // With burstMax=20 and refillRate=0, exactly 20 requests of cost=1 should be allowed!
            int numThreads = 100;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneGate = new CountDownLatch(numThreads);
            AtomicInteger allowedCount = new AtomicInteger(0);

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        if (limiter.allow("user-race", 1) == Decision.ALLOW) {
                            allowedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown();
            doneGate.await();
            executor.shutdown();

            // Under race condition, allowedCount will often exceed 20!
            assertEquals(20, allowedCount.get(),
                    "Under contention, allowed requests must never exceed burst capacity (20) in trial " + trial);
        }
    }
}
