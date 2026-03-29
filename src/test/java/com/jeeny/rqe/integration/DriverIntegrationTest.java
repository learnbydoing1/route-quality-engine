package com.jeeny.rqe.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeeny.rqe.dto.*;
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
class DriverIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TripResponse generateTrip(String driverId, double jitter, double tunnel, double drift, double driftProb) throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(24.7136).startLng(46.6753)
                .endLat(24.7500).endLng(46.7200)
                .jitterMeters(jitter)
                .tunnelFraction(tunnel)
                .driftMeters(drift)
                .driftProbability(driftProb)
                .driverId(driverId)
                .build();

        MvcResult result = mockMvc.perform(post("/api/trips/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TripResponse.class);
    }

    @Test
    void driverSummary_noTrips_returnsEmptyResult() throws Exception {
        MvcResult result = mockMvc.perform(get("/drivers/UNKNOWN_DRIVER/summary"))
                .andExpect(status().isOk())
                .andReturn();

        DriverSummary summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), DriverSummary.class);
        assertEquals("UNKNOWN_DRIVER", summary.getDriverId());
        assertEquals(0, summary.getTotalTrips());
        assertEquals(0.0, summary.getAverageTrustScore());
    }

    @Test
    void driverSummary_multipleTrips_correctAggregation() throws Exception {
        String driverId = "D-INTEG-" + System.currentTimeMillis();
        generateTrip(driverId, 0, 0, 0, 0);
        generateTrip(driverId, 15, 0.1, 0, 0);
        generateTrip(driverId, 100, 0.5, 200, 0.3);

        MvcResult result = mockMvc.perform(get("/drivers/" + driverId + "/summary"))
                .andExpect(status().isOk())
                .andReturn();

        DriverSummary summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), DriverSummary.class);

        assertEquals(driverId, summary.getDriverId());
        assertEquals(3, summary.getTotalTrips());
        assertTrue(summary.getAverageTrustScore() > 0);
        assertTrue(summary.getMinTrustScore() <= summary.getAverageTrustScore());
        assertTrue(summary.getMaxTrustScore() >= summary.getAverageTrustScore());
        assertNotNull(summary.getTrustLevelDistribution());
        assertNotNull(summary.getBillingDecisionDistribution());
    }

    @Test
    void driverSummary_isolatedByDriver() throws Exception {
        String driver1 = "D-ISO-A-" + System.currentTimeMillis();
        String driver2 = "D-ISO-B-" + System.currentTimeMillis();

        generateTrip(driver1, 0, 0, 0, 0);
        generateTrip(driver2, 100, 0.5, 200, 0.3);

        MvcResult result1 = mockMvc.perform(get("/drivers/" + driver1 + "/summary"))
                .andExpect(status().isOk()).andReturn();
        MvcResult result2 = mockMvc.perform(get("/drivers/" + driver2 + "/summary"))
                .andExpect(status().isOk()).andReturn();

        DriverSummary s1 = objectMapper.readValue(result1.getResponse().getContentAsString(), DriverSummary.class);
        DriverSummary s2 = objectMapper.readValue(result2.getResponse().getContentAsString(), DriverSummary.class);

        assertEquals(1, s1.getTotalTrips());
        assertEquals(1, s2.getTotalTrips());
        assertTrue(s1.getAverageTrustScore() > s2.getAverageTrustScore(),
                "Clean driver should have higher trust than noisy driver");
    }

    @Test
    void driverSummary_includesTrustDistribution() throws Exception {
        String driverId = "D-DIST-" + System.currentTimeMillis();
        generateTrip(driverId, 0, 0, 0, 0);

        MvcResult result = mockMvc.perform(get("/drivers/" + driverId + "/summary"))
                .andExpect(status().isOk()).andReturn();

        DriverSummary summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), DriverSummary.class);

        assertNotNull(summary.getTrustLevelDistribution());
        long total = summary.getTrustLevelDistribution().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(summary.getTotalTrips(), total);
    }

    @Test
    void driverSummary_includesTotalDistance() throws Exception {
        String driverId = "D-TDIST-" + System.currentTimeMillis();
        generateTrip(driverId, 0, 0, 0, 0);

        MvcResult result = mockMvc.perform(get("/drivers/" + driverId + "/summary"))
                .andExpect(status().isOk()).andReturn();

        DriverSummary summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), DriverSummary.class);

        assertTrue(summary.getTotalDistanceKm() > 0, "Total distance should be positive");
    }

    @Test
    void reasonCodeSummary_noTrips_returnsEmpty() throws Exception {
        MvcResult result = mockMvc.perform(get("/drivers/UNKNOWN_DRIVER/reason-code-summary"))
                .andExpect(status().isOk())
                .andReturn();

        ReasonCodeSummary summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), ReasonCodeSummary.class);
        assertEquals("UNKNOWN_DRIVER", summary.getDriverId());
        assertEquals(0, summary.getTotalTrips());
        assertEquals(0, summary.getTotalAnomalies());
    }

    @Test
    void reasonCodeSummary_correctAnomalyFrequency() throws Exception {
        String driverId = "D-RC-" + System.currentTimeMillis();
        generateTrip(driverId, 50, 0.3, 100, 0.2);

        MvcResult result = mockMvc.perform(get("/drivers/" + driverId + "/reason-code-summary"))
                .andExpect(status().isOk()).andReturn();

        ReasonCodeSummary summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), ReasonCodeSummary.class);

        assertEquals(1, summary.getTotalTrips());
        assertTrue(summary.getTotalAnomalies() > 0, "Noisy trip should produce anomalies");
        assertNotNull(summary.getAnomalyFrequency());
        assertFalse(summary.getAnomalyFrequency().isEmpty());
    }

    @Test
    void reasonCodeSummary_billingDecisionDistribution() throws Exception {
        String driverId = "D-BD-" + System.currentTimeMillis();
        generateTrip(driverId, 0, 0, 0, 0);
        generateTrip(driverId, 100, 0.5, 200, 0.3);

        MvcResult result = mockMvc.perform(get("/drivers/" + driverId + "/reason-code-summary"))
                .andExpect(status().isOk()).andReturn();

        ReasonCodeSummary summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), ReasonCodeSummary.class);

        assertNotNull(summary.getBillingDecisionDistribution());
        assertFalse(summary.getBillingDecisionDistribution().isEmpty());
    }

    @Test
    void reasonCodeSummary_lowTrustPercentage() throws Exception {
        String driverId = "D-LTP-" + System.currentTimeMillis();
        generateTrip(driverId, 0, 0, 0, 0);
        generateTrip(driverId, 100, 0.5, 200, 0.3);

        MvcResult result = mockMvc.perform(get("/drivers/" + driverId + "/reason-code-summary"))
                .andExpect(status().isOk()).andReturn();

        ReasonCodeSummary summary = objectMapper.readValue(
                result.getResponse().getContentAsString(), ReasonCodeSummary.class);

        assertEquals(2, summary.getTotalTrips());
        assertTrue(summary.getLowTrustTripPercentage() > 0, "Should have some low trust trips");
        assertTrue(summary.getLowTrustTripPercentage() <= 100);
    }

    @Test
    void reasonCodeSummary_matchesActualAnomalyCount() throws Exception {
        String driverId = "D-MATCH-" + System.currentTimeMillis();
        TripResponse trip = generateTrip(driverId, 50, 0.2, 0, 0);

        MvcResult reportResult = mockMvc.perform(get("/route-quality/report/" + trip.getId()))
                .andExpect(status().isOk()).andReturn();
        RouteQualityReport report = objectMapper.readValue(
                reportResult.getResponse().getContentAsString(), RouteQualityReport.class);

        MvcResult rcResult = mockMvc.perform(get("/drivers/" + driverId + "/reason-code-summary"))
                .andExpect(status().isOk()).andReturn();
        ReasonCodeSummary rcSummary = objectMapper.readValue(
                rcResult.getResponse().getContentAsString(), ReasonCodeSummary.class);

        long anomalyFreqTotal = rcSummary.getAnomalyFrequency().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(rcSummary.getTotalAnomalies(), anomalyFreqTotal,
                "Total anomalies should match sum of frequency map");
    }
}
