package com.jeeny.rqe.service;

import com.jeeny.rqe.dto.DriverSummary;
import com.jeeny.rqe.dto.ReasonCodeSummary;
import com.jeeny.rqe.model.*;
import com.jeeny.rqe.repository.AnomalyRepository;
import com.jeeny.rqe.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private AnomalyRepository anomalyRepository;

    @InjectMocks
    private DriverService driverService;

    private Instant now;
    private Instant thirtyDaysAgo;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
    }

    private Trip buildTrip(String driverId, double trustScore, TrustLevel level,
                           BillingDecision decision, double distKm, Instant createdAt) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .driverId(driverId)
                .trustScore(trustScore)
                .trustLevel(level)
                .billingDecision(decision)
                .plannedDistanceKm(distKm)
                .reviewStatus(trustScore < 50 ? ReviewStatus.PENDING_REVIEW : ReviewStatus.NOT_REVIEWED)
                .createdAt(createdAt)
                .build();
    }

    private Anomaly buildAnomaly(UUID tripId, AnomalyType type) {
        return Anomaly.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .anomalyType(type)
                .affectedPoints(1)
                .description(type.name())
                .build();
    }

    // --- DriverSummary tests ---

    @Test
    void summary_noTrips_returnsEmptyDefaults() {
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(Collections.emptyList());

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);

        assertEquals("D1", summary.getDriverId());
        assertEquals(0, summary.getTotalTrips());
        assertEquals(0.0, summary.getAverageTrustScore());
        assertEquals(0.0, summary.getMinTrustScore());
        assertEquals(0.0, summary.getMaxTrustScore());
        assertTrue(summary.getTrustLevelDistribution().isEmpty());
        assertTrue(summary.getBillingDecisionDistribution().isEmpty());
        assertEquals("STABLE", summary.getTrendIndicator());
    }

    @Test
    void summary_singleTrip_matchesTripValues() {
        Trip trip = buildTrip("D1", 85.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 10.0, now.minus(1, ChronoUnit.DAYS));
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(List.of(trip));

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);

        assertEquals(1, summary.getTotalTrips());
        assertEquals(85.0, summary.getAverageTrustScore());
        assertEquals(85.0, summary.getMinTrustScore());
        assertEquals(85.0, summary.getMaxTrustScore());
    }

    @Test
    void summary_multipleTrips_correctAggregation() {
        List<Trip> trips = List.of(
                buildTrip("D1", 90.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0, now.minus(3, ChronoUnit.DAYS)),
                buildTrip("D1", 60.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 8.0, now.minus(2, ChronoUnit.DAYS)),
                buildTrip("D1", 30.0, TrustLevel.LOW, BillingDecision.USE_ESTIMATE, 12.0, now.minus(1, ChronoUnit.DAYS))
        );
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(trips);

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);

        assertEquals(3, summary.getTotalTrips());
        assertEquals(60.0, summary.getAverageTrustScore(), 0.01);
        assertEquals(30.0, summary.getMinTrustScore());
        assertEquals(90.0, summary.getMaxTrustScore());
        assertEquals(25.0, summary.getTotalDistanceKm());
    }

    @Test
    void summary_trustLevelDistribution_countsCorrectly() {
        List<Trip> trips = List.of(
                buildTrip("D1", 95.0, TrustLevel.HIGH, BillingDecision.USE_ACTUALS, 5.0, now.minus(3, ChronoUnit.DAYS)),
                buildTrip("D1", 92.0, TrustLevel.HIGH, BillingDecision.USE_ACTUALS, 5.0, now.minus(2, ChronoUnit.DAYS)),
                buildTrip("D1", 30.0, TrustLevel.LOW, BillingDecision.USE_ESTIMATE, 5.0, now.minus(1, ChronoUnit.DAYS))
        );
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(trips);

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);

        assertEquals(2L, summary.getTrustLevelDistribution().get(TrustLevel.HIGH));
        assertEquals(1L, summary.getTrustLevelDistribution().get(TrustLevel.LOW));
        assertNull(summary.getTrustLevelDistribution().get(TrustLevel.MEDIUM));
    }

    @Test
    void summary_billingDecisionDistribution_countsCorrectly() {
        List<Trip> trips = List.of(
                buildTrip("D1", 95.0, TrustLevel.HIGH, BillingDecision.USE_ACTUALS, 5.0, now.minus(3, ChronoUnit.DAYS)),
                buildTrip("D1", 60.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0, now.minus(2, ChronoUnit.DAYS)),
                buildTrip("D1", 30.0, TrustLevel.LOW, BillingDecision.USE_ESTIMATE, 5.0, now.minus(1, ChronoUnit.DAYS))
        );
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(trips);

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);

        assertEquals(1L, summary.getBillingDecisionDistribution().get(BillingDecision.USE_ACTUALS));
        assertEquals(1L, summary.getBillingDecisionDistribution().get(BillingDecision.HYBRID));
        assertEquals(1L, summary.getBillingDecisionDistribution().get(BillingDecision.USE_ESTIMATE));
    }

    @Test
    void summary_trendImproving_whenRecentScoresHigher() {
        List<Trip> trips = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double score = 40.0 + i * 8.0;
            trips.add(buildTrip("D1", score, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0,
                    now.minus(8 - i, ChronoUnit.DAYS)));
        }
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(trips);

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);
        assertEquals("IMPROVING", summary.getTrendIndicator());
    }

    @Test
    void summary_trendDeclining_whenRecentScoresLower() {
        List<Trip> trips = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double score = 96.0 - i * 8.0;
            trips.add(buildTrip("D1", score, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0,
                    now.minus(8 - i, ChronoUnit.DAYS)));
        }
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(trips);

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);
        assertEquals("DECLINING", summary.getTrendIndicator());
    }

    @Test
    void summary_trendStable_whenScoresFlat() {
        List<Trip> trips = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            trips.add(buildTrip("D1", 75.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0,
                    now.minus(8 - i, ChronoUnit.DAYS)));
        }
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(trips);

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);
        assertEquals("STABLE", summary.getTrendIndicator());
    }

    @Test
    void summary_trendStable_whenFewerThan4Trips() {
        List<Trip> trips = List.of(
                buildTrip("D1", 30.0, TrustLevel.LOW, BillingDecision.USE_ESTIMATE, 5.0, now.minus(3, ChronoUnit.DAYS)),
                buildTrip("D1", 90.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0, now.minus(1, ChronoUnit.DAYS))
        );
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(trips);

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);
        assertEquals("STABLE", summary.getTrendIndicator());
    }

    @Test
    void summary_lowTrustPercentage_correctCalculation() {
        List<Trip> trips = List.of(
                buildTrip("D1", 95.0, TrustLevel.HIGH, BillingDecision.USE_ACTUALS, 5.0, now.minus(4, ChronoUnit.DAYS)),
                buildTrip("D1", 80.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0, now.minus(3, ChronoUnit.DAYS)),
                buildTrip("D1", 30.0, TrustLevel.LOW, BillingDecision.USE_ESTIMATE, 5.0, now.minus(2, ChronoUnit.DAYS)),
                buildTrip("D1", 20.0, TrustLevel.LOW, BillingDecision.USE_ESTIMATE, 5.0, now.minus(1, ChronoUnit.DAYS))
        );
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(trips);

        DriverSummary summary = driverService.getDriverSummary("D1", thirtyDaysAgo, now);
        assertEquals(50.0, summary.getLowTrustTripPercentage(), 0.01);
    }

    // --- ReasonCodeSummary tests ---

    @Test
    void reasonCodes_noAnomalies_returnsZeroCounts() {
        Trip trip = buildTrip("D1", 95.0, TrustLevel.HIGH, BillingDecision.USE_ACTUALS, 5.0, now.minus(1, ChronoUnit.DAYS));
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(List.of(trip));
        when(anomalyRepository.findByTripIdIn(anyList())).thenReturn(Collections.emptyList());

        ReasonCodeSummary summary = driverService.getReasonCodeSummary("D1", thirtyDaysAgo, now);

        assertEquals(1, summary.getTotalTrips());
        assertEquals(0, summary.getTotalAnomalies());
        assertTrue(summary.getAnomalyFrequency().isEmpty());
        assertNull(summary.getMostCommonAnomaly());
        assertEquals(0.0, summary.getAnomaliesPerTrip());
    }

    @Test
    void reasonCodes_mixedAnomalies_correctFrequency() {
        Trip t1 = buildTrip("D1", 60.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0, now.minus(2, ChronoUnit.DAYS));
        Trip t2 = buildTrip("D1", 40.0, TrustLevel.LOW, BillingDecision.USE_ESTIMATE, 5.0, now.minus(1, ChronoUnit.DAYS));
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(List.of(t1, t2));

        List<Anomaly> anomalies = List.of(
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t2.getId(), AnomalyType.UNREALISTIC_SPEED),
                buildAnomaly(t2.getId(), AnomalyType.UNREALISTIC_SPEED),
                buildAnomaly(t2.getId(), AnomalyType.UNREALISTIC_SPEED),
                buildAnomaly(t2.getId(), AnomalyType.SIGNAL_GAP)
        );
        when(anomalyRepository.findByTripIdIn(anyList())).thenReturn(anomalies);

        ReasonCodeSummary summary = driverService.getReasonCodeSummary("D1", thirtyDaysAgo, now);

        assertEquals(9, summary.getTotalAnomalies());
        assertEquals(5L, summary.getAnomalyFrequency().get(AnomalyType.GPS_JITTER));
        assertEquals(3L, summary.getAnomalyFrequency().get(AnomalyType.UNREALISTIC_SPEED));
        assertEquals(1L, summary.getAnomalyFrequency().get(AnomalyType.SIGNAL_GAP));
    }

    @Test
    void reasonCodes_mostCommonAnomaly_correct() {
        Trip t1 = buildTrip("D1", 60.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0, now.minus(1, ChronoUnit.DAYS));
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(List.of(t1));

        List<Anomaly> anomalies = List.of(
                buildAnomaly(t1.getId(), AnomalyType.SIGNAL_GAP),
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER)
        );
        when(anomalyRepository.findByTripIdIn(anyList())).thenReturn(anomalies);

        ReasonCodeSummary summary = driverService.getReasonCodeSummary("D1", thirtyDaysAgo, now);
        assertEquals(AnomalyType.GPS_JITTER, summary.getMostCommonAnomaly());
    }

    @Test
    void reasonCodes_anomaliesPerTrip_correctAverage() {
        Trip t1 = buildTrip("D1", 60.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0, now.minus(2, ChronoUnit.DAYS));
        Trip t2 = buildTrip("D1", 70.0, TrustLevel.MEDIUM, BillingDecision.HYBRID, 5.0, now.minus(1, ChronoUnit.DAYS));
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(List.of(t1, t2));

        List<Anomaly> anomalies = List.of(
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t1.getId(), AnomalyType.GPS_JITTER),
                buildAnomaly(t2.getId(), AnomalyType.GPS_JITTER)
        );
        when(anomalyRepository.findByTripIdIn(anyList())).thenReturn(anomalies);

        ReasonCodeSummary summary = driverService.getReasonCodeSummary("D1", thirtyDaysAgo, now);
        assertEquals(2.0, summary.getAnomaliesPerTrip(), 0.01);
    }

    @Test
    void reasonCodes_lowTrustPercentage_matchesSummary() {
        List<Trip> trips = List.of(
                buildTrip("D1", 95.0, TrustLevel.HIGH, BillingDecision.USE_ACTUALS, 5.0, now.minus(3, ChronoUnit.DAYS)),
                buildTrip("D1", 30.0, TrustLevel.LOW, BillingDecision.USE_ESTIMATE, 5.0, now.minus(2, ChronoUnit.DAYS)),
                buildTrip("D1", 20.0, TrustLevel.LOW, BillingDecision.USE_ESTIMATE, 5.0, now.minus(1, ChronoUnit.DAYS))
        );
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(trips);
        when(anomalyRepository.findByTripIdIn(anyList())).thenReturn(Collections.emptyList());

        ReasonCodeSummary rcs = driverService.getReasonCodeSummary("D1", thirtyDaysAgo, now);
        DriverSummary ds = driverService.getDriverSummary("D1", thirtyDaysAgo, now);

        assertEquals(ds.getLowTrustTripPercentage(), rcs.getLowTrustTripPercentage(), 0.01);
    }

    @Test
    void reasonCodes_noTrips_returnsEmpty() {
        when(tripRepository.findByDriverIdAndCreatedAtBetween(eq("D1"), any(), any()))
                .thenReturn(Collections.emptyList());

        ReasonCodeSummary summary = driverService.getReasonCodeSummary("D1", thirtyDaysAgo, now);

        assertEquals(0, summary.getTotalTrips());
        assertEquals(0, summary.getTotalAnomalies());
        assertTrue(summary.getAnomalyFrequency().isEmpty());
    }
}
