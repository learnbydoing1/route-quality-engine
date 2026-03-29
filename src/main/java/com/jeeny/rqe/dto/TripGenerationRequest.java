package com.jeeny.rqe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripGenerationRequest {

    @NotNull
    private Double startLat;

    @NotNull
    private Double startLng;

    @NotNull
    private Double endLat;

    @NotNull
    private Double endLng;

    @Min(0) @Max(200)
    @Builder.Default
    private double jitterMeters = 0.0;

    @Min(0) @Max(1)
    @Builder.Default
    private double tunnelFraction = 0.0;

    @Min(0) @Max(500)
    @Builder.Default
    private double driftMeters = 0.0;

    @Min(0) @Max(1)
    @Builder.Default
    private double driftProbability = 0.0;

    private String driverId;
}
