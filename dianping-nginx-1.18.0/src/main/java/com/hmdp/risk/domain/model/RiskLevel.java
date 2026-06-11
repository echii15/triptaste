package com.hmdp.risk.domain.model;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static RiskLevel fromScore(int score) {
        if (score >= 10) {
            return CRITICAL;
        }
        if (score >= 7) {
            return HIGH;
        }
        if (score >= 4) {
            return MEDIUM;
        }
        return LOW;
    }

    public static RiskLevel max(RiskLevel a, RiskLevel b) {
        if (a == null) {
            return b == null ? LOW : b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
