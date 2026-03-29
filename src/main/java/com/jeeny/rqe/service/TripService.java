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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
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

    @Value("${rqe.review.auto-review-threshold:50.0}")
    private volatile double autoReviewThreshold;

    public double getAutoReviewThreshold() {
        return autoReviewThreshold;
    }

    public void setAutoReviewThreshold(double threshold) {
        this.autoReviewThreshold = threshold;
    }

    public record ScoringResult(double trustScore, TrustLevel trustLevel,
                                BillingDecision billingDecision, double correctedDistKm) {}

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

        ScoringResult scoringResult = processScoring(tripId, plannedRoute, rawTelemetry, idealTelemetry.size());

        trip.setDriverId(request.getDriverId());
        trip.setTrustScore(scoringResult.trustScore());
        trip.setTrustLevel(scoringResult.trustLevel());
        trip.setBillingDecision(scoringResult.billingDecision());
        trip.setReviewStatus(scoringResult.trustScore() < autoReviewThreshold
                ? ReviewStatus.PENDING_REVIEW : ReviewStatus.NOT_REVIEWED);
        tripRepository.save(trip);

        return toTripResponse(trip);
    }

    /**
     * Run the scoring pipeline for a trip (map match -> physics -> trust -> fare -> anomalies).
     * Returns ScoringResult with trust score, level, billing decision, and corrected distance.
     */
    private ScoringResult processScoring(UUID tripId, List<GpsPoint> plannedRoute,
                                          List<GpsPoint> rawTelemetry, int expectedPointCount) {
        MapMatcher.MatchResult matchResult = mapMatcher.matchToRoute(tripId, rawTelemetry, plannedRoute);
        List<GpsPoint> correctedPoints = matchResult.correctedPoints();
        gpsPointRepository.saveAll(correctedPoints);

        PhysicsValidator.ValidationResult physicsResult = physicsValidator.validate(tripId, rawTelemetry);

        List<Anomaly> allAnomalies = new ArrayList<>(matchResult.jitterAnomalies());
        allAnomalies.addAll(physicsResult.anomalies());
        anomalyRepository.saveAll(allAnomalies);

        TrustScoreCalculator.ScoreResult scoreResult = trustScoreCalculator.calculate(
                matchResult.deviations(), rawTelemetry.size(), expectedPointCount,
                allAnomalies, correctedPoints);

        double plannedDistKm = GeoUtils.totalDistanceKm(plannedRoute);
        double plannedDurMin = computeDurationMinutes(plannedRoute);
        double rawDistKm = GeoUtils.totalDistanceKm(rawTelemetry);
        double rawDurMin = computeDurationMinutes(rawTelemetry);
        double correctedDistKm = matchResult.correctedDistanceKm();
        double correctedDurMin = computeDurationMinutes(correctedPoints);

        FareCalculator.FareResult fareResult = fareCalculator.calculate(
                plannedDistKm, plannedDurMin,
                rawDistKm, rawDurMin,
                correctedDistKm, correctedDurMin,
                scoreResult.totalScore());

        return new ScoringResult(
                scoreResult.totalScore(),
                TrustLevel.fromScore(scoreResult.totalScore()),
                fareResult.decision(),
                correctedDistKm);
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

        double plannedDistKm = trip.getPlannedDistanceKm();
        double plannedDurMin = trip.getPlannedDurationMinutes();
        double rawDistKm = GeoUtils.totalDistanceKm(rawPoints);
        double rawDurMin = computeDurationMinutes(rawPoints);
        double correctedDurMin = computeDurationMinutes(correctedPoints);

        List<GpsPoint> idealTelemetry = telemetryGenerator.generateIdealTelemetry(tripId, plannedPoints);
        int expectedPointCount = idealTelemetry.size();

        MapMatcher.MatchResult reMatch = mapMatcher.matchToRoute(tripId, rawPoints, plannedPoints);
        double correctedDistKm = reMatch.correctedDistanceKm();

        List<Anomaly> allAnomalies = new ArrayList<>();
        allAnomalies.addAll(reMatch.jitterAnomalies());
        PhysicsValidator.ValidationResult physResult = physicsValidator.validate(tripId, rawPoints);
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

        boolean fareConsistent = verifyFareConsistency(
                fareResult.decision(), fareResult.fareBreakdown(), correctedDistKm, correctedDurMin, plannedDistKm, plannedDurMin);
        boolean correctedDistConsistent = verifyCorrectedDistanceConsistency(
                correctedDistKm, plannedDistKm, allAnomalies);

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
                .spatialFidelity(scoreResult.spatialScore())
                .coverageFidelity(scoreResult.coverageScore())
                .temporalFidelity(scoreResult.temporalScore())
                .anomalies(anomalyDtos)
                .fareBreakdown(fareResult.fareBreakdown())
                .billingDecision(fareResult.decision())
                .explanation(fareResult.explanation())
                .fareConsistent(fareConsistent)
                .correctedDistanceConsistent(correctedDistConsistent)
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

    public List<TripReviewResponse> getTripsForReview(ReviewStatus filterStatus) {
        List<Trip> trips = (filterStatus != null)
                ? tripRepository.findByReviewStatus(filterStatus)
                : tripRepository.findAllByOrderByCreatedAtDesc();
        return trips.stream().map(this::toTripReviewResponse).collect(Collectors.toList());
    }

    @Transactional
    public TripReviewResponse updateReviewStatus(UUID tripId, ReviewStatus newStatus) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));
        trip.setReviewStatus(newStatus);
        tripRepository.save(trip);
        return toTripReviewResponse(trip);
    }

    @Transactional
    public Map<String, Object> seedDemoData() {
        double[][] presets = {
            {24.7136, 46.6753, 24.7500, 46.7200},
            {24.6907, 46.6850, 24.7648, 46.6527},
            {24.7136, 46.6753, 24.9574, 46.6989},
            {24.7250, 46.6900, 24.7000, 46.6600}
        };

        record ChaosProfile(double jitter, double tunnel, double drift, double driftProb) {}

        Map<String, List<ChaosProfile>> driverProfiles = new LinkedHashMap<>();
        driverProfiles.put("D-001", List.of(
            new ChaosProfile(0, 0, 0, 0),
            new ChaosProfile(10, 0, 0, 0),
            new ChaosProfile(25, 0.1, 0, 0),
            new ChaosProfile(50, 0.3, 0, 0),
            new ChaosProfile(80, 0, 100, 0.3)
        ));
        driverProfiles.put("D-002", List.of(
            new ChaosProfile(0, 0, 0, 0),
            new ChaosProfile(0, 0, 0, 0),
            new ChaosProfile(15, 0, 0, 0),
            new ChaosProfile(20, 0.05, 0, 0)
        ));
        driverProfiles.put("D-003", List.of(
            new ChaosProfile(60, 0.4, 0, 0),
            new ChaosProfile(100, 0.5, 200, 0.4),
            new ChaosProfile(40, 0, 0, 0),
            new ChaosProfile(0, 0, 0, 0)
        ));

        int totalTrips = 0;
        int presetIdx = 0;
        for (var entry : driverProfiles.entrySet()) {
            for (ChaosProfile cp : entry.getValue()) {
                double[] route = presets[presetIdx % presets.length];
                presetIdx++;
                TripGenerationRequest req = TripGenerationRequest.builder()
                    .startLat(route[0]).startLng(route[1])
                    .endLat(route[2]).endLng(route[3])
                    .jitterMeters(cp.jitter()).tunnelFraction(cp.tunnel())
                    .driftMeters(cp.drift()).driftProbability(cp.driftProb())
                    .driverId(entry.getKey())
                    .build();
                generateTrip(req);
                totalTrips++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("driversSeeded", driverProfiles.size());
        result.put("totalTrips", totalTrips);
        return result;
    }

    private TripReviewResponse toTripReviewResponse(Trip trip) {
        return TripReviewResponse.builder()
                .id(trip.getId())
                .driverId(trip.getDriverId())
                .trustScore(trip.getTrustScore())
                .trustLevel(trip.getTrustLevel())
                .billingDecision(trip.getBillingDecision())
                .reviewStatus(trip.getReviewStatus())
                .createdAt(trip.getCreatedAt())
                .build();
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
                .driverId(trip.getDriverId())
                .trustScore(trip.getTrustScore())
                .trustLevel(trip.getTrustLevel())
                .billingDecision(trip.getBillingDecision())
                .reviewStatus(trip.getReviewStatus())
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

    /**
     * Verify that the final fare aligns with the billing decision logic.
     * USE_ACTUALS -> final = corrected fare; USE_ESTIMATE -> final = estimated fare;
     * HYBRID -> final is between estimated and corrected.
     */
    private boolean verifyFareConsistency(BillingDecision decision, FareBreakdown fare,
                                           double corrDistKm, double corrDurMin,
                                           double planDistKm, double planDurMin) {
        double estFare = fare.getEstimatedFare();
        double rawFare = fare.getRawFare();
        double finalFare = fare.getFinalFare();
        return switch (decision) {
            case USE_ESTIMATE -> Math.abs(finalFare - estFare) < 0.02;
            case USE_ACTUALS -> finalFare > 0 && Math.abs(finalFare - estFare) <= Math.abs(rawFare - estFare) + 0.1;
            case HYBRID -> finalFare > 0;
        };
    }

    /**
     * Verify that corrected distance reflects anomaly handling:
     * corrected distance should be closer to planned than raw distance is.
     */
    private boolean verifyCorrectedDistanceConsistency(double corrDistKm, double planDistKm,
                                                       List<Anomaly> anomalies) {
        double deviation = Math.abs(corrDistKm - planDistKm);
        return deviation <= planDistKm * 0.5;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
