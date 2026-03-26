package com.jeeny.rqe.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeeny.rqe.dto.RouteQualityReport;
import com.jeeny.rqe.dto.TripGenerationRequest;
import com.jeeny.rqe.dto.TripResponse;
import com.jeeny.rqe.model.BillingDecision;
import com.jeeny.rqe.model.TrustLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * KPI Validation Tests -- these tests verify the two key success metrics:
 *
 * 1. Simulation Accuracy: The engine identifies "Low Trust" trips when noise > 20m is injected.
 * 2. Revenue Protection: 100% of "Tunnel" trips default to Estimated Fare (never $0).
 */
@SpringBootTest
@AutoConfigureMockMvc
class KpiValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final double START_LAT = 24.7136;
    private static final double START_LNG = 46.6753;
    private static final double END_LAT = 24.7500;
    private static final double END_LNG = 46.7200;

    // ===== KPI 1: Simulation Accuracy =====

    @Test
    @DisplayName("KPI-1: Noise > 20m with tunnel effect should produce LOW trust level")
    void kpi1_highNoiseProducesLowTrust() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(START_LAT)
                .startLng(START_LNG)
                .endLat(END_LAT)
                .endLng(END_LNG)
                .jitterMeters(50)
                .tunnelFraction(0.3)
                .driftMeters(100)
                .driftProbability(0.2)
                .build();

        RouteQualityReport report = generateAndGetReport(request);

        assertTrue(report.getTrustScore() < 50,
                "KPI-1 FAILED: Noise=50m + tunnel=30% should produce Low trust. Got score: " + report.getTrustScore());
        assertEquals(TrustLevel.LOW, report.getTrustLevel(),
                "KPI-1 FAILED: Trust level should be LOW");
    }

    @Test
    @DisplayName("KPI-1: Noise of 30m should produce non-HIGH trust")
    void kpi1_moderateNoiseReducesTrust() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(START_LAT)
                .startLng(START_LNG)
                .endLat(END_LAT)
                .endLng(END_LNG)
                .jitterMeters(30)
                .tunnelFraction(0)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        RouteQualityReport report = generateAndGetReport(request);

        assertTrue(report.getTrustScore() <= 90,
                "KPI-1: 30m noise should not produce High trust. Got: " + report.getTrustScore());
    }

    @Test
    @DisplayName("KPI-1: Zero noise should produce HIGH trust")
    void kpi1_zeroNoiseProducesHighTrust() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(START_LAT)
                .startLng(START_LNG)
                .endLat(END_LAT)
                .endLng(END_LNG)
                .jitterMeters(0)
                .tunnelFraction(0)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        RouteQualityReport report = generateAndGetReport(request);

        assertTrue(report.getTrustScore() > 90,
                "KPI-1: Clean trip should produce High trust. Got: " + report.getTrustScore());
        assertEquals(TrustLevel.HIGH, report.getTrustLevel());
    }

    // ===== KPI 2: Revenue Protection =====

    @Test
    @DisplayName("KPI-2: 100% tunnel trip defaults to estimated fare (never $0)")
    void kpi2_fullTunnelDefaultsToEstimatedFare() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(START_LAT)
                .startLng(START_LNG)
                .endLat(END_LAT)
                .endLng(END_LNG)
                .jitterMeters(0)
                .tunnelFraction(0.95)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        RouteQualityReport report = generateAndGetReport(request);

        assertTrue(report.getFareBreakdown().getFinalFare() > 0,
                "KPI-2 FAILED: Final fare must never be $0. Got: " + report.getFareBreakdown().getFinalFare());

        assertTrue(report.getFareBreakdown().getEstimatedFare() > 0,
                "KPI-2 FAILED: Estimated fare must be > 0. Got: " + report.getFareBreakdown().getEstimatedFare());

        if (report.getTrustLevel() == TrustLevel.LOW) {
            assertEquals(report.getFareBreakdown().getEstimatedFare(),
                    report.getFareBreakdown().getFinalFare(),
                    "KPI-2 FAILED: Low trust trip should bill on estimated fare");
            assertEquals(BillingDecision.USE_ESTIMATE, report.getBillingDecision());
        }
    }

    @Test
    @DisplayName("KPI-2: High tunnel fraction produces low coverage and triggers estimate billing")
    void kpi2_highTunnelTriggersEstimateBilling() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(START_LAT)
                .startLng(START_LNG)
                .endLat(END_LAT)
                .endLng(END_LNG)
                .jitterMeters(0)
                .tunnelFraction(0.8)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        RouteQualityReport report = generateAndGetReport(request);

        assertTrue(report.getRawTelemetry().getPointCount() < report.getPlannedRoute().getPointCount(),
                "KPI-2: Tunnel should reduce raw telemetry points");

        assertTrue(report.getFareBreakdown().getFinalFare() > 0,
                "KPI-2: Final fare must never be zero for tunnel trips");
    }

    @Test
    @DisplayName("KPI-2: Tunnel trip fare never charges $0 for missing distance")
    void kpi2_tunnelTripNeverChargesZero() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(START_LAT)
                .startLng(START_LNG)
                .endLat(END_LAT)
                .endLng(END_LNG)
                .jitterMeters(0)
                .tunnelFraction(0.9)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        RouteQualityReport report = generateAndGetReport(request);

        double finalFare = report.getFareBreakdown().getFinalFare();
        double estimatedFare = report.getFareBreakdown().getEstimatedFare();

        assertTrue(finalFare > 0, "Revenue protection: fare must never be $0");
        assertTrue(estimatedFare > 0, "Estimated fare must be positive");

        assertNotEquals(BillingDecision.USE_ACTUALS, report.getBillingDecision(),
                "KPI-2: High tunnel should not bill on actuals (missing data)");
    }

    @Test
    @DisplayName("KPI-2: Multiple tunnel trips all produce non-zero fares")
    void kpi2_multipleTunnelTripsAllHaveNonZeroFares() throws Exception {
        for (int i = 0; i < 5; i++) {
            TripGenerationRequest request = TripGenerationRequest.builder()
                    .startLat(START_LAT + i * 0.01)
                    .startLng(START_LNG + i * 0.01)
                    .endLat(END_LAT + i * 0.01)
                    .endLng(END_LNG + i * 0.01)
                    .jitterMeters(0)
                    .tunnelFraction(0.85)
                    .driftMeters(0)
                    .driftProbability(0)
                    .build();

            RouteQualityReport report = generateAndGetReport(request);

            assertTrue(report.getFareBreakdown().getFinalFare() > 0,
                    "KPI-2 iteration " + i + ": fare must be > $0, got: " + report.getFareBreakdown().getFinalFare());
        }
    }

    private RouteQualityReport generateAndGetReport(TripGenerationRequest request) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/trips/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        TripResponse trip = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), TripResponse.class);

        MvcResult reportResult = mockMvc.perform(get("/route-quality/report/" + trip.getId()))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                reportResult.getResponse().getContentAsString(), RouteQualityReport.class);
    }
}
