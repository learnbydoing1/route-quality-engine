package com.jeeny.rqe.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeeny.rqe.dto.ReviewRequest;
import com.jeeny.rqe.dto.TripGenerationRequest;
import com.jeeny.rqe.dto.TripResponse;
import com.jeeny.rqe.dto.TripReviewResponse;
import com.jeeny.rqe.model.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TripReviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private TripResponse generateTrip(TripGenerationRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/trips/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TripResponse.class);
    }

    private TripGenerationRequest cleanRequest() {
        return TripGenerationRequest.builder()
                .startLat(24.7136).startLng(46.6753)
                .endLat(24.7500).endLng(46.7200)
                .jitterMeters(0).tunnelFraction(0)
                .driftMeters(0).driftProbability(0)
                .build();
    }

    private TripGenerationRequest noisyRequest() {
        return TripGenerationRequest.builder()
                .startLat(24.7136).startLng(46.6753)
                .endLat(24.7500).endLng(46.7200)
                .jitterMeters(100).tunnelFraction(0.5)
                .driftMeters(200).driftProbability(0.3)
                .build();
    }

    @Test
    void getReview_returnsListOfTrips() throws Exception {
        MvcResult result = mockMvc.perform(get("/trips/review"))
                .andExpect(status().isOk())
                .andReturn();

        List<TripReviewResponse> reviews = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TripReviewResponse>>() {});
        assertNotNull(reviews);
    }

    @Test
    void generateTrip_cleanGps_defaultNotReviewed() throws Exception {
        TripResponse trip = generateTrip(cleanRequest());

        assertNotNull(trip.getReviewStatus());
        assertEquals(ReviewStatus.NOT_REVIEWED, trip.getReviewStatus());
        assertTrue(trip.getTrustScore() > 50, "Clean trip should be above threshold");
    }

    @Test
    void generateTrip_heavyNoise_autoPendingReview() throws Exception {
        TripResponse trip = generateTrip(noisyRequest());

        assertTrue(trip.getTrustScore() < 50, "Heavy noise trip should be below threshold, got: " + trip.getTrustScore());
        assertEquals(ReviewStatus.PENDING_REVIEW, trip.getReviewStatus());
    }

    @Test
    void generateTrip_withDriverId_driverIdPersisted() throws Exception {
        TripGenerationRequest request = TripGenerationRequest.builder()
                .startLat(24.7136).startLng(46.6753)
                .endLat(24.7500).endLng(46.7200)
                .driverId("D-TEST-001")
                .build();

        TripResponse trip = generateTrip(request);
        assertEquals("D-TEST-001", trip.getDriverId());
    }

    @Test
    void postReview_approve_statusUpdated() throws Exception {
        TripResponse trip = generateTrip(cleanRequest());

        ReviewRequest reviewReq = ReviewRequest.builder()
                .reviewStatus(ReviewStatus.APPROVED).build();

        MvcResult result = mockMvc.perform(post("/trips/" + trip.getId() + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andReturn();

        TripReviewResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), TripReviewResponse.class);
        assertEquals(ReviewStatus.APPROVED, response.getReviewStatus());
    }

    @Test
    void postReview_reject_statusUpdated() throws Exception {
        TripResponse trip = generateTrip(cleanRequest());

        ReviewRequest reviewReq = ReviewRequest.builder()
                .reviewStatus(ReviewStatus.REJECTED).build();

        MvcResult result = mockMvc.perform(post("/trips/" + trip.getId() + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isOk())
                .andReturn();

        TripReviewResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), TripReviewResponse.class);
        assertEquals(ReviewStatus.REJECTED, response.getReviewStatus());
    }

    @Test
    void getReview_filterByStatus_returnsOnlyMatching() throws Exception {
        generateTrip(noisyRequest());

        MvcResult result = mockMvc.perform(get("/trips/review?status=PENDING_REVIEW"))
                .andExpect(status().isOk())
                .andReturn();

        List<TripReviewResponse> reviews = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TripReviewResponse>>() {});

        assertFalse(reviews.isEmpty(), "Should have at least one pending review trip");
        reviews.forEach(r -> assertEquals(ReviewStatus.PENDING_REVIEW, r.getReviewStatus()));
    }

    @Test
    void getReview_noFilter_returnsAll() throws Exception {
        generateTrip(cleanRequest());

        MvcResult result = mockMvc.perform(get("/trips/review"))
                .andExpect(status().isOk())
                .andReturn();

        List<TripReviewResponse> reviews = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<TripReviewResponse>>() {});
        assertFalse(reviews.isEmpty());
    }

    @Test
    void postReview_nonExistentTrip_returns404() throws Exception {
        ReviewRequest reviewReq = ReviewRequest.builder()
                .reviewStatus(ReviewStatus.APPROVED).build();

        mockMvc.perform(post("/trips/00000000-0000-0000-0000-000000000000/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewReq)))
                .andExpect(status().isNotFound());
    }

    @Test
    void postReview_invalidBody_returns400() throws Exception {
        TripResponse trip = generateTrip(cleanRequest());

        mockMvc.perform(post("/trips/" + trip.getId() + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
