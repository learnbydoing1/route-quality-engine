package com.jeeny.rqe.service;

import com.jeeny.rqe.dto.*;
import com.jeeny.rqe.model.*;
import com.jeeny.rqe.repository.AnomalyRepository;
import com.jeeny.rqe.repository.GpsPointRepository;
import com.jeeny.rqe.repository.TripRepository;
import com.jeeny.rqe.service.engine.MapMatcher;
import com.jeeny.rqe.service.engine.PhysicsValidator;
import com.jeeny.rqe.service.engine.TrustScoreCalculator;
import com.jeeny.rqe.service.fare.FareCalculator;
import com.jeeny.rqe.service.generator.ChaosInjector;
import com.jeeny.rqe.service.generator.RouteGenerator;
import com.jeeny.rqe.service.generator.TelemetryGenerator;
import com.jeeny.rqe.util.GeoUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final GpsPointRepository gpsPointRepository;
    private final AnomalyRepository anomalyRepository;
    private final RouteGenerator routeGenerator;
    private final TelemetryGenerator telemetryGenerator;
    private final ChaosInjector chaosInjector;
    private final MapMatcher mapMatcher;
    private final PhysicsValidator physicsValidator;
    private final TrustScoreCalculator trustScoreCalculator;
    private final FareCalculator fareCalculator;

    /**
     * Generate a synthetic trip: planned route -> ideal telemetry -> chaos injection -> scoring.
     */
    @Transactional
    public TripResponse generateTrip(TripGenerationRequest request) {
        ChaosConfig chaosConfig = ChaosConfig.builder()
                .jitterMeters(request.getJitterMeters())
                .tunnelFraction(request.getTunnelFraction())
                .driftMeters(request.getDriftMeters())
                .driftProbability(request.getDriftProbability())
                .build();

        Trip trip = Trip.builder()
                .startLat(request.getStartLat())
                .startLng(request.getStartLng())
                .endLat(request.getEndLat())
                .endLng(request.getEndLng())
                .chaosConfig(chaosConfig)
                .build();

        trip = tripRepository.save(trip);
        UUID tripId = trip.getId();

        List<GpsPoint> plannedRoute = routeGenerator.generatePlannedRoute(
                tripId, request.getStartLat(), request.getStartLng(),
                request.getEndLat(), request.getEndLng());

        double plannedDistKm = GeoUtils.totalDistanceKm(plannedRoute);
        double plannedDurationMin = computeDurationMinutes(plannedRoute);
        trip.setPlannedDistanceKm(plannedDistKm);
        trip.setPlannedDurationMinutes(plannedDurationMin);
        tripRepository.save(trip);

        gpsPointRepository.saveAll(plannedRoute);

        List<GpsPoint> idealTelemetry = telemetryGenerator.generateIdealTelemetry(tripId, plannedRoute);
        List<GpsPoint> rawTelemetry = chaosInjector.injectChaos(idealTelemetry, chaosConfig);
        gpsPointRepository.saveAll(rawTelemetry);

        processScoring(tripId, plannedRoute, rawTelemetry, idealTelemetry.size());

        return toTripResponse(trip);
    }

    /**
     * Run the scoring pipeline for a trip (map match -> physics -> trust -> anomalies).
     */
    private void processScoring(UUID tripId, List<GpsPoint> plannedRoute,
                                 List<GpsPoint> rawTelemetry, int expectedPointCount) {
        MapMatcher.MatchResult matchResult = mapMatcher.matchToRoute(tripId, rawTelemetry, plannedRoute);
        List<GpsPoint> correctedPoints = matchResult.correctedPoints();
        gpsPointRepository.saveAll(correctedPoints);

        PhysicsValidator.ValidationResult physicsResult = physicsValidator.validate(tripId, correctedPoints);

        List<Anomaly> allAnomalies = new ArrayList<>(matchResult.jitterAnomalies());
        allAnomalies.addAll(physicsResult.anomalies());
        anomalyRepository.saveAll(allAnomalies);
    }

    /**
     * Build the full route quality report for a trip.
     */
    public RouteQualityReport buildReport(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        List<GpsPoint> plannedPoints = gpsPointRepository
                .findByTripIdAndPointTypeOrderBySequenceIndex(tripId, PointType.PLANNED);
        List<GpsPoint> rawPoints = gpsPointRepository
                .findByTripIdAndPointTypeOrderBySequenceIndex(tripId, PointType.RAW);
        List<GpsPoint> correctedPoints = gpsPointRepository
                .findByTripIdAndPointTypeOrderBySequenceIndex(tripId, PointType.CORRECTED);
        List<Anomaly> anomalies = anomalyRepository.findByTripId(tripId);

        double plannedDistKm = trip.getPlannedDistanceKm();
        double plannedDurMin = trip.getPlannedDurationMinutes();
        double rawDistKm = GeoUtils.totalDistanceKm(rawPoints);
        double rawDurMin = computeDurationMinutes(rawPoints);
        double correctedDistKm = GeoUtils.totalDistanceKm(correctedPoints);
        double correctedDurMin = computeDurationMinutes(correctedPoints);

        List<GpsPoint> idealTelemetry = telemetryGenerator.generateIdealTelemetry(tripId, plannedPoints);
        int expectedPointCount = idealTelemetry.size();

        MapMatcher.MatchResult reMatch = mapMatcher.matchToRoute(tripId, rawPoints, plannedPoints);

        List<Anomaly> allAnomalies = new ArrayList<>();
        allAnomalies.addAll(reMatch.jitterAnomalies());
        PhysicsValidator.ValidationResult physResult = physicsValidator.validate(tripId, correctedPoints);
        allAnomalies.addAll(physResult.anomalies());

        TrustScoreCalculator.ScoreResult scoreResult = trustScoreCalculator.calculate(
                reMatch.deviations(), rawPoints.size(), expectedPointCount,
                allAnomalies, correctedPoints);

        FareCalculator.FareResult fareResult = fareCalculator.calculate(
                plannedDistKm, plannedDurMin,
                rawDistKm, rawDurMin,
                correctedDistKm, correctedDurMin,
                scoreResult.totalScore());

        List<AnomalyDto> anomalyDtos = allAnomalies.stream()
                .map(a -> AnomalyDto.builder()
                        .type(a.getAnomalyType())
                        .description(a.getDescription())
                        .affectedPoints(a.getAffectedPoints())
                        .gapDurationSeconds(a.getGapDurationSeconds())
                        .build())
                .collect(Collectors.toList());

        return RouteQualityReport.builder()
                .tripId(tripId)
                .plannedRoute(RouteSummary.builder()
                        .distanceKm(round(plannedDistKm))
                        .durationMinutes(round(plannedDurMin))
                        .pointCount(plannedPoints.size())
                        .build())
                .rawTelemetry(RouteSummary.builder()
                        .distanceKm(round(rawDistKm))
                        .durationMinutes(round(rawDurMin))
                        .pointCount(rawPoints.size())
                        .build())
                .correctedRoute(RouteSummary.builder()
                        .distanceKm(round(correctedDistKm))
                        .durationMinutes(round(correctedDurMin))
                        .pointCount(correctedPoints.size())
                        .build())
                .trustScore(scoreResult.totalScore())
                .trustLevel(TrustLevel.fromScore(scoreResult.totalScore()))
                .anomalies(anomalyDtos)
                .fareBreakdown(fareResult.fareBreakdown())
                .billingDecision(fareResult.decision())
                .explanation(fareResult.explanation())
                .build();
    }

    public List<TripResponse> getAllTrips() {
        return tripRepository.findAll().stream()
                .map(this::toTripResponse)
                .collect(Collectors.toList());
    }

    public TripResponse getTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));
        return toTripResponse(trip);
    }

    private TripResponse toTripResponse(Trip trip) {
        return TripResponse.builder()
                .id(trip.getId())
                .startLat(trip.getStartLat())
                .startLng(trip.getStartLng())
                .endLat(trip.getEndLat())
                .endLng(trip.getEndLng())
                .plannedDistanceKm(trip.getPlannedDistanceKm())
                .plannedDurationMinutes(trip.getPlannedDurationMinutes())
                .chaosConfig(trip.getChaosConfig())
                .createdAt(trip.getCreatedAt())
                .build();
    }

    private double computeDurationMinutes(List<GpsPoint> points) {
        if (points == null || points.size() < 2) return 0.0;
        long seconds = Duration.between(
                points.get(0).getTimestamp(),
                points.get(points.size() - 1).getTimestamp()
        ).getSeconds();
        return seconds / 60.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
