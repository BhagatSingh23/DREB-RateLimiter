package com.dreb.ratelimit;

/**
 * Wall-clock implementation of {@link Clock}, backed by System.nanoTime()
 * for monotonicity within a single JVM.
 */
public final class SystemClock implements Clock {

    private static final long START_NANOS = System.nanoTime();

    @Override
    public double now() {
        return (System.nanoTime() - START_NANOS) / 1_000_000_000.0;
    }
}
