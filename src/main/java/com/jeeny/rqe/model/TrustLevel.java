package com.jeeny.rqe.model;

public enum TrustLevel {
    HIGH,
    MEDIUM,
    LOW;

    public static TrustLevel fromScore(double score) {
        if (score > 90.0) return HIGH;
        if (score >= 50.0) return MEDIUM;
        return LOW;
    }
}
