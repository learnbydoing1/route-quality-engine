package com.jeeny.rqe.controller;

import com.jeeny.rqe.dto.ReviewRequest;
import com.jeeny.rqe.dto.TripReviewResponse;
import com.jeeny.rqe.model.ReviewStatus;
import com.jeeny.rqe.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/trips")
@RequiredArgsConstructor
public class TripReviewController {

    private final TripService tripService;

    @GetMapping("/review")
    public ResponseEntity<List<TripReviewResponse>> getTripsForReview(
            @RequestParam(required = false) ReviewStatus status) {
        return ResponseEntity.ok(tripService.getTripsForReview(status));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<TripReviewResponse> updateReviewStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(tripService.updateReviewStatus(id, request.getReviewStatus()));
    }

    @GetMapping("/review/threshold")
    public ResponseEntity<Map<String, Double>> getThreshold() {
        return ResponseEntity.ok(Map.of("threshold", tripService.getAutoReviewThreshold()));
    }

    @PutMapping("/review/threshold")
    public ResponseEntity<Map<String, Double>> setThreshold(@RequestBody Map<String, Double> body) {
        double val = body.getOrDefault("threshold", 50.0);
        if (val < 0 || val > 100) {
            throw new IllegalArgumentException("Threshold must be between 0 and 100");
        }
        tripService.setAutoReviewThreshold(val);
        return ResponseEntity.ok(Map.of("threshold", val));
    }
}
