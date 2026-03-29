package com.jeeny.rqe.service;

import com.jeeny.rqe.dto.DriverSummary;
import com.jeeny.rqe.dto.ReasonCodeSummary;
import com.jeeny.rqe.model.*;
import com.jeeny.rqe.repository.AnomalyRepository;
import com.jeeny.rqe.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final TripRepository tripRepository;
    private final AnomalyRepository anomalyRepository;

    public DriverSummary getDriverSummary(String driverId, Instant from, Instant to) {
        List<Trip> trips = tripRepository.findByDriverIdAndCreatedAtBetween(driverId, from, to);

        if (trips.isEmpty()) {
            return buildEmptySummary(driverId, from, to);
        }

        List<Double> scores = trips.stream()
                .map(Trip::getTrustScore)
                .filter(Objects::nonNull)
                .toList();

        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        Map<TrustLevel, Long> trustDist = trips.stream()
                .filter(t -> t.getTrustLevel() != null)
                .collect(Collectors.groupingBy(Trip::getTrustLevel, Collectors.counting()));

        Map<BillingDecision, Long> billingDist = trips.stream()
                .filter(t -> t.getBillingDecision() != null)
                .collect(Collectors.groupingBy(Trip::getBillingDecision, Collectors.counting()));

        double totalDistKm = trips.stream().mapToDouble(Trip::getPlannedDistanceKm).sum();

        long lowTrustCount = scores.stream().filter(s -> s < 50.0).count();
        double lowTrustPct = (scores.isEmpty()) ? 0.0 : (lowTrustCount * 100.0) / scores.size();

        String trend = computeTrend(trips);

        return DriverSummary.builder()
                .driverId(driverId)
                .totalTrips(trips.size())
                .averageTrustScore(round(avg))
                .minTrustScore(round(min))
                .maxTrustScore(round(max))
                .trustLevelDistribution(trustDist)
                .billingDecisionDistribution(billingDist)
                .totalDistanceKm(round(totalDistKm))
                .lowTrustTripPercentage(round(lowTrustPct))
                .trendIndicator(trend)
                .periodStart(from)
                .periodEnd(to)
                .build();
    }

    public ReasonCodeSummary getReasonCodeSummary(String driverId, Instant from, Instant to) {
        List<Trip> trips = tripRepository.findByDriverIdAndCreatedAtBetween(driverId, from, to);

        if (trips.isEmpty()) {
            return buildEmptyReasonCodeSummary(driverId);
        }

        List<UUID> tripIds = trips.stream().map(Trip::getId).toList();
        List<Anomaly> anomalies = anomalyRepository.findByTripIdIn(tripIds);

        Map<AnomalyType, Long> anomalyFreq = anomalies.stream()
                .collect(Collectors.groupingBy(Anomaly::getAnomalyType, Collectors.counting()));

        AnomalyType mostCommon = anomalyFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        List<Double> scores = trips.stream()
                .map(Trip::getTrustScore)
                .filter(Objects::nonNull)
                .toList();
        int lowTrustCount = (int) scores.stream().filter(s -> s < 50.0).count();
        double lowTrustPct = scores.isEmpty() ? 0.0 : (lowTrustCount * 100.0) / scores.size();

        Map<BillingDecision, Long> billingDist = trips.stream()
                .filter(t -> t.getBillingDecision() != null)
                .collect(Collectors.groupingBy(Trip::getBillingDecision, Collectors.counting()));

        double anomaliesPerTrip = trips.isEmpty() ? 0.0 : (double) anomalies.size() / trips.size();

        return ReasonCodeSummary.builder()
                .driverId(driverId)
                .totalTrips(trips.size())
                .totalAnomalies(anomalies.size())
                .lowTrustTripCount(lowTrustCount)
                .lowTrustTripPercentage(round(lowTrustPct))
                .billingDecisionDistribution(billingDist)
                .anomalyFrequency(anomalyFreq)
                .mostCommonAnomaly(mostCommon)
                .anomaliesPerTrip(round(anomaliesPerTrip))
                .build();
    }

    String computeTrend(List<Trip> trips) {
        List<Trip> sorted = trips.stream()
                .sorted(Comparator.comparing(Trip::getCreatedAt))
                .toList();

        if (sorted.size() < 4) {
            return "STABLE";
        }

        int quarter = sorted.size() / 4;
        double firstQuarterAvg = sorted.subList(0, quarter).stream()
                .map(Trip::getTrustScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);

        double lastQuarterAvg = sorted.subList(sorted.size() - quarter, sorted.size()).stream()
                .map(Trip::getTrustScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average().orElse(0.0);

        double diff = lastQuarterAvg - firstQuarterAvg;
        if (diff > 5.0) return "IMPROVING";
        if (diff < -5.0) return "DECLINING";
        return "STABLE";
    }

    private DriverSummary buildEmptySummary(String driverId, Instant from, Instant to) {
        return DriverSummary.builder()
                .driverId(driverId)
                .totalTrips(0)
                .averageTrustScore(0.0)
                .minTrustScore(0.0)
                .maxTrustScore(0.0)
                .trustLevelDistribution(Collections.emptyMap())
                .billingDecisionDistribution(Collections.emptyMap())
                .totalDistanceKm(0.0)
                .lowTrustTripPercentage(0.0)
                .trendIndicator("STABLE")
                .periodStart(from)
                .periodEnd(to)
                .build();
    }

    private ReasonCodeSummary buildEmptyReasonCodeSummary(String driverId) {
        return ReasonCodeSummary.builder()
                .driverId(driverId)
                .totalTrips(0)
                .totalAnomalies(0)
                .lowTrustTripCount(0)
                .lowTrustTripPercentage(0.0)
                .billingDecisionDistribution(Collections.emptyMap())
                .anomalyFrequency(Collections.emptyMap())
                .mostCommonAnomaly(null)
                .anomaliesPerTrip(0.0)
                .build();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
