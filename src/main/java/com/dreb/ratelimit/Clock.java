package com.dreb.ratelimit;

/**
 * Abstracts "now" so limiters are testable with a fake clock and so a
 * distributed deployment can substitute a shared clock source (e.g. Redis
 * TIME) without touching decision logic. See Section 8 ("Clock skew in
 * distributed deployments") in the paper.
 */
public interface Clock {
    /** Current time in fractional seconds. */
    double now();
}
