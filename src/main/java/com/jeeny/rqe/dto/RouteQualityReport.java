package com.jeeny.rqe.dto;

import com.jeeny.rqe.model.BillingDecision;
import com.jeeny.rqe.model.TrustLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteQualityReport {

    private UUID tripId;

    private RouteSummary plannedRoute;
    private RouteSummary rawTelemetry;
    private RouteSummary correctedRoute;

    private double trustScore;
    private TrustLevel trustLevel;
    private double spatialFidelity;
    private double coverageFidelity;
    private double temporalFidelity;

    private List<AnomalyDto> anomalies;

    private FareBreakdown fareBreakdown;
    private BillingDecision billingDecision;
    private String explanation;

    private boolean fareConsistent;
    private boolean correctedDistanceConsistent;
}
