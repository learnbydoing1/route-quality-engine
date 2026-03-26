package com.jeeny.rqe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteSummary {

    private double distanceKm;
    private double durationMinutes;
    private int pointCount;
}
