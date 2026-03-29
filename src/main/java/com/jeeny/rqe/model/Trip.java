package com.jeeny.rqe.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private double startLat;
    private double startLng;
    private double endLat;
    private double endLng;

    /** Planned route distance in km */
    private double plannedDistanceKm;

    /** Planned route duration in minutes */
    private double plannedDurationMinutes;

    @Embedded
    @Builder.Default
    private ChaosConfig chaosConfig = new ChaosConfig();

    @Builder.Default
    private Instant createdAt = Instant.now();

    private String driverId;

    @Column(name = "trust_score")
    private Double trustScore;

    @Enumerated(EnumType.STRING)
    private TrustLevel trustLevel;

    @Enumerated(EnumType.STRING)
    private BillingDecision billingDecision;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.NOT_REVIEWED;
}
