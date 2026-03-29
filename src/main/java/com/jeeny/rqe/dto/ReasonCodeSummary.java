package com.jeeny.rqe.dto;

import com.jeeny.rqe.model.AnomalyType;
import com.jeeny.rqe.model.BillingDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonCodeSummary {

    private String driverId;
    private int totalTrips;
    private long totalAnomalies;
    private int lowTrustTripCount;
    private double lowTrustTripPercentage;
    private Map<BillingDecision, Long> billingDecisionDistribution;
    private Map<AnomalyType, Long> anomalyFrequency;
    private AnomalyType mostCommonAnomaly;
    private double anomaliesPerTrip;
}
