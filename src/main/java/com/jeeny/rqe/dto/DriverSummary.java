package com.jeeny.rqe.dto;

import com.jeeny.rqe.model.BillingDecision;
import com.jeeny.rqe.model.TrustLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverSummary {

    private String driverId;
    private int totalTrips;
    private double averageTrustScore;
    private double minTrustScore;
    private double maxTrustScore;
    private Map<TrustLevel, Long> trustLevelDistribution;
    private Map<BillingDecision, Long> billingDecisionDistribution;
    private double totalDistanceKm;
    private double lowTrustTripPercentage;
    private String trendIndicator;
    private Instant periodStart;
    private Instant periodEnd;
}
