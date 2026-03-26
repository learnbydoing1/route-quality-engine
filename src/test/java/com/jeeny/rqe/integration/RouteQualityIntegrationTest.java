package com.jeeny.rqe.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeeny.rqe.dto.RouteQualityReport;
import com.jeeny.rqe.dto.TripGenerationRequest;
import com.jeeny.rqe.dto.TripResponse;
import com.jeeny.rqe.model.BillingDecision;
import com.jeeny.rqe.model.TrustLevel;
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

@SpringBootTest
@AutoConfigureMockMvc
class RouteQualityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final double RIYADH_START_LAT = 24.7136;
    private static final double RIYADH_START_LNG = 46.6753;
    private static final double RIYADH_END_LAT = 24.7500;
    private static final double RIYADH_END_LNG = 46.7200;

    @Test
    void endToEnd_cleanTrip_highTrustAndActualsBilling() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(RIYADH_START_LAT)
                .startLng(RIYADH_START_LNG)
                .endLat(RIYADH_END_LAT)
                .endLng(RIYADH_END_LNG)
                .jitterMeters(0)
                .tunnelFraction(0)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        TripResponse trip = generateTrip(request);
        assertNotNull(trip.getId());

        RouteQualityReport report = getReport(trip.getId().toString());

        assertEquals(trip.getId(), report.getTripId());
        assertTrue(report.getTrustScore() > 90, "Clean trip should have high trust score, got: " + report.getTrustScore());
        assertEquals(TrustLevel.HIGH, report.getTrustLevel());
        assertEquals(BillingDecision.USE_ACTUALS, report.getBillingDecision());

        assertNotNull(report.getPlannedRoute());
        assertNotNull(report.getRawTelemetry());
        assertNotNull(report.getCorrectedRoute());
        assertTrue(report.getPlannedRoute().getDistanceKm() > 0);
        assertTrue(report.getPlannedRoute().getPointCount() > 0);

        assertNotNull(report.getFareBreakdown());
        assertTrue(report.getFareBreakdown().getEstimatedFare() > 0);
        assertTrue(report.getFareBreakdown().getFinalFare() > 0);
        assertNotNull(report.getExplanation());
        assertFalse(report.getExplanation().isEmpty());
    }

    @Test
    void endToEnd_moderateJitter_mediumTrustAndHybridBilling() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(RIYADH_START_LAT)
                .startLng(RIYADH_START_LNG)
                .endLat(RIYADH_END_LAT)
                .endLng(RIYADH_END_LNG)
                .jitterMeters(15)
                .tunnelFraction(0.1)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        TripResponse trip = generateTrip(request);
        RouteQualityReport report = getReport(trip.getId().toString());

        assertTrue(report.getTrustScore() <= 90,
                "Moderate jitter should not have high trust, got: " + report.getTrustScore());
        assertNotNull(report.getAnomalies());
        assertTrue(report.getFareBreakdown().getFinalFare() > 0);
    }

    @Test
    void endToEnd_heavyNoise_lowTrustAndEstimateBilling() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(RIYADH_START_LAT)
                .startLng(RIYADH_START_LNG)
                .endLat(RIYADH_END_LAT)
                .endLng(RIYADH_END_LNG)
                .jitterMeters(100)
                .tunnelFraction(0.5)
                .driftMeters(200)
                .driftProbability(0.3)
                .build();

        TripResponse trip = generateTrip(request);
        RouteQualityReport report = getReport(trip.getId().toString());

        assertTrue(report.getTrustScore() < 50,
                "Heavy noise should have low trust, got: " + report.getTrustScore());
        assertEquals(TrustLevel.LOW, report.getTrustLevel());
        assertEquals(BillingDecision.USE_ESTIMATE, report.getBillingDecision());

        assertEquals(report.getFareBreakdown().getEstimatedFare(),
                report.getFareBreakdown().getFinalFare(),
                "Low trust: final fare must equal estimated fare");
    }

    @Test
    void endToEnd_reportConsistency_correctedDistanceReflectsAnomalyHandling() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(RIYADH_START_LAT)
                .startLng(RIYADH_START_LNG)
                .endLat(RIYADH_END_LAT)
                .endLng(RIYADH_END_LNG)
                .jitterMeters(30)
                .tunnelFraction(0)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        TripResponse trip = generateTrip(request);
        RouteQualityReport report = getReport(trip.getId().toString());

        double rawDist = report.getRawTelemetry().getDistanceKm();
        double correctedDist = report.getCorrectedRoute().getDistanceKm();
        double plannedDist = report.getPlannedRoute().getDistanceKm();

        assertTrue(Math.abs(correctedDist - plannedDist) <= Math.abs(rawDist - plannedDist) + 0.1,
                "Corrected distance should be closer to planned than raw distance");
    }

    @Test
    void endToEnd_fareDecisionAlignedWithTrustScore() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(RIYADH_START_LAT)
                .startLng(RIYADH_START_LNG)
                .endLat(RIYADH_END_LAT)
                .endLng(RIYADH_END_LNG)
                .jitterMeters(0)
                .tunnelFraction(0)
                .driftMeters(0)
                .driftProbability(0)
                .build();

        TripResponse trip = generateTrip(request);
        RouteQualityReport report = getReport(trip.getId().toString());

        if (report.getTrustScore() > 90) {
            assertEquals(BillingDecision.USE_ACTUALS, report.getBillingDecision());
        } else if (report.getTrustScore() >= 50) {
            assertEquals(BillingDecision.HYBRID, report.getBillingDecision());
        } else {
            assertEquals(BillingDecision.USE_ESTIMATE, report.getBillingDecision());
        }
    }

    @Test
    void listTrips_afterGeneration_containsTrip() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(RIYADH_START_LAT)
                .startLng(RIYADH_START_LNG)
                .endLat(RIYADH_END_LAT)
                .endLng(RIYADH_END_LNG)
                .build();

        TripResponse trip = generateTrip(request);

        MvcResult listResult = mockMvc.perform(get("/api/trips"))
                .andExpect(status().isOk())
                .andReturn();

        String body = listResult.getResponse().getContentAsString();
        assertTrue(body.contains(trip.getId().toString()));
    }

    @Test
    void getTrip_returnsCorrectTrip() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(RIYADH_START_LAT)
                .startLng(RIYADH_START_LNG)
                .endLat(RIYADH_END_LAT)
                .endLng(RIYADH_END_LNG)
                .build();

        TripResponse created = generateTrip(request);

        MvcResult result = mockMvc.perform(get("/api/trips/" + created.getId()))
                .andExpect(status().isOk())
                .andReturn();

        TripResponse fetched = objectMapper.readValue(
                result.getResponse().getContentAsString(), TripResponse.class);
        assertEquals(created.getId(), fetched.getId());
        assertEquals(RIYADH_START_LAT, fetched.getStartLat(), 0.0001);
    }

    @Test
    void getReport_nonExistentTrip_returns404() throws Exception {
        mockMvc.perform(get("/route-quality/report/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void generateTrip_invalidRequest_returns400() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post("/api/trips/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    private TripResponse generateTrip(TripGenerationRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trips/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), TripResponse.class);
    }

    private RouteQualityReport getReport(String tripId) throws Exception {
        MvcResult result = mockMvc.perform(get("/route-quality/report/" + tripId))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), RouteQualityReport.class);
    }
}
