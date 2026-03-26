package com.jeeny.rqe.service.engine;

import com.jeeny.rqe.model.Anomaly;
import com.jeeny.rqe.model.AnomalyType;
import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.util.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PhysicsValidator {

    private final double maxSpeedKmh;
    private final int signalGapThresholdSeconds;

    public PhysicsValidator(
            @Value("${rqe.scoring.max-speed-kmh:150.0}") double maxSpeedKmh,
            @Value("${rqe.scoring.signal-gap-threshold-seconds:10}") int signalGapThresholdSeconds) {
        this.maxSpeedKmh = maxSpeedKmh;
        this.signalGapThresholdSeconds = signalGapThresholdSeconds;
    }

    /**
     * Result of physics validation containing detected anomalies and per-segment speeds.
     */
    public record ValidationResult(List<Anomaly> anomalies, List<Double> segmentSpeedsKmh) {}

    /**
     * Validate physics of corrected GPS points: check speeds and time gaps.
     */
    public ValidationResult validate(UUID tripId, List<GpsPoint> points) {
        List<Anomaly> anomalies = new ArrayList<>();
        List<Double> speeds = new ArrayList<>();

        if (points == null || points.size() < 2) {
            return new ValidationResult(anomalies, speeds);
        }

        for (int i = 1; i < points.size(); i++) {
            GpsPoint prev = points.get(i - 1);
            GpsPoint curr = points.get(i);

            double distKm = GeoUtils.haversineKm(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude());

            long timeDiffSeconds = Duration.between(prev.getTimestamp(), curr.getTimestamp()).getSeconds();

            if (timeDiffSeconds > signalGapThresholdSeconds) {
                anomalies.add(Anomaly.builder()
                        .tripId(tripId)
                        .anomalyType(AnomalyType.SIGNAL_GAP)
                        .startIndex(prev.getSequenceIndex())
                        .endIndex(curr.getSequenceIndex())
                        .affectedPoints(curr.getSequenceIndex() - prev.getSequenceIndex())
                        .gapDurationSeconds(timeDiffSeconds)
                        .description(String.format(
                                "Signal gap of %ds detected between indices %d and %d",
                                timeDiffSeconds, prev.getSequenceIndex(), curr.getSequenceIndex()))
                        .build());
            }

            double speedKmh = 0.0;
            if (timeDiffSeconds > 0) {
                double timeHours = timeDiffSeconds / 3600.0;
                speedKmh = distKm / timeHours;
            }
            speeds.add(speedKmh);

            if (speedKmh > maxSpeedKmh) {
                anomalies.add(Anomaly.builder()
                        .tripId(tripId)
                        .anomalyType(AnomalyType.UNREALISTIC_SPEED)
                        .startIndex(prev.getSequenceIndex())
                        .endIndex(curr.getSequenceIndex())
                        .affectedPoints(2)
                        .description(String.format(
                                "Unrealistic speed of %.1f km/h detected between indices %d and %d (max: %.0f km/h)",
                                speedKmh, prev.getSequenceIndex(), curr.getSequenceIndex(), maxSpeedKmh))
                        .build());
            }
        }

        return new ValidationResult(anomalies, speeds);
    }
}
