package com.jeeny.rqe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareBreakdown {

    private double estimatedFare;
    private double rawFare;
    private double finalFare;
}
