package com.dreb.ratelimit;

/**
 * Deterministic {@link Clock} whose value is set explicitly. Used in unit
 * tests and in the simulation demo (see {@code demo.Main}) so runs are
 * reproducible, unlike {@link SystemClock}.
 */
public final class ManualClock implements Clock {

    private double t;

    public ManualClock(double initial) {
        this.t = initial;
    }

    public void set(double t) {
        this.t = t;
    }

    public void advance(double dt) {
        this.t += dt;
    }

    @Override
    public double now() {
        return t;
    }
}
