package com.naghma.accounts;

/** Subscription tiers. Quotas are tracks analyzed per calendar month; -1 = unlimited. */
public enum Tier {
    FREE(50, false),
    DJ(1_500, true),
    PRO(-1, true),
    AGENCY(-1, true);

    private final int monthlyTrackQuota;
    private final boolean setBuilder;

    Tier(int monthlyTrackQuota, boolean setBuilder) {
        this.monthlyTrackQuota = monthlyTrackQuota;
        this.setBuilder = setBuilder;
    }

    public int monthlyTrackQuota() {
        return monthlyTrackQuota;
    }

    public boolean setBuilder() {
        return setBuilder;
    }

    public boolean isPaid() {
        return this != FREE;
    }
}
