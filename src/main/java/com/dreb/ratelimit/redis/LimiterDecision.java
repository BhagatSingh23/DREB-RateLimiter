package com.dreb.ratelimit.redis;

/**
 * Production rate limiter decision containing rich audit metadata.
 */
public record LimiterDecision(
        DecisionStatus status,
        int cost,
        double burstTokensRemaining,
        double sustainedTokensRemaining,
        double idleCreditRemaining,
        double abuseScore,
        long decisionTimestampMicros,
        boolean isCached,
        String rejectionReason
) {
    public enum DecisionStatus {
        ALLOW,
        DENY,
        IDEMPOTENCY_CONFLICT,
        INFRASTRUCTURE_ERROR
    }

    public boolean isAllowed() {
        return status == DecisionStatus.ALLOW;
    }

    public static LimiterDecision allow(int cost, double b, double s, double i, double k, long tsMicros, boolean cached) {
        return new LimiterDecision(DecisionStatus.ALLOW, cost, b, s, i, k, tsMicros, cached, null);
    }

    public static LimiterDecision deny(int cost, double b, double s, double i, double k, long tsMicros, boolean cached, String reason) {
        return new LimiterDecision(DecisionStatus.DENY, cost, b, s, i, k, tsMicros, cached, reason);
    }

    public static LimiterDecision conflict(int cost, String reason) {
        return new LimiterDecision(DecisionStatus.IDEMPOTENCY_CONFLICT, cost, -1.0, -1.0, -1.0, -1.0, 0L, true, reason);
    }

    public static LimiterDecision infrastructureError(int cost, String reason, boolean failOpen) {
        DecisionStatus status = failOpen ? DecisionStatus.ALLOW : DecisionStatus.INFRASTRUCTURE_ERROR;
        return new LimiterDecision(status, cost, -1.0, -1.0, -1.0, -1.0, 0L, false, reason);
    }
}
