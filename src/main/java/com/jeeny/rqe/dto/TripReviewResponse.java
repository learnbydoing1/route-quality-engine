package com.jeeny.rqe.dto;

import com.jeeny.rqe.model.BillingDecision;
import com.jeeny.rqe.model.ReviewStatus;
import com.jeeny.rqe.model.TrustLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripReviewResponse {

    private UUID id;
    private String driverId;
    private Double trustScore;
    private TrustLevel trustLevel;
    private BillingDecision billingDecision;
    private ReviewStatus reviewStatus;
    private Instant createdAt;
}
