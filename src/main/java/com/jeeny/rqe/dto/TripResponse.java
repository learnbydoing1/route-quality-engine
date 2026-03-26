package com.jeeny.rqe.dto;

import com.jeeny.rqe.model.ChaosConfig;
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
public class TripResponse {

    private UUID id;
    private double startLat;
    private double startLng;
    private double endLat;
    private double endLng;
    private double plannedDistanceKm;
    private double plannedDurationMinutes;
    private ChaosConfig chaosConfig;
    private Instant createdAt;
}
