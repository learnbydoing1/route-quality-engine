package com.jeeny.rqe.dto;

import com.jeeny.rqe.model.AnomalyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyDto {

    private AnomalyType type;
    private String description;
    private int affectedPoints;
    private double gapDurationSeconds;
}
