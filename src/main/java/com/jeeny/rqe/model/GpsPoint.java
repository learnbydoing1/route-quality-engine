package com.jeeny.rqe.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gps_points")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class GpsPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tripId;

    private double latitude;
    private double longitude;

    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PointType pointType;

    private int sequenceIndex;
}
