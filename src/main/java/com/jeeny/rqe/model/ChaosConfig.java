package com.jeeny.rqe.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ChaosConfig {

    /** Gaussian noise standard deviation in meters (0 = no jitter) */
    @Builder.Default
    private double jitterMeters = 0.0;

    /** Fraction of telemetry points to delete in contiguous chunks (0.0-1.0) */
    @Builder.Default
    private double tunnelFraction = 0.0;

    /** Magnitude of random teleportation events in meters (0 = no drift) */
    @Builder.Default
    private double driftMeters = 0.0;

    /** Probability of a drift event occurring at any given point (0.0-1.0) */
    @Builder.Default
    private double driftProbability = 0.0;
}
