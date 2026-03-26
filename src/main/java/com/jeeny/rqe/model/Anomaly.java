package com.jeeny.rqe.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "anomalies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tripId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnomalyType anomalyType;

    private int startIndex;
    private int endIndex;

    @Column(length = 500)
    private String description;

    /** Number of GPS points affected */
    private int affectedPoints;

    /** Duration of gap in seconds (for SIGNAL_GAP) */
    private double gapDurationSeconds;
}
