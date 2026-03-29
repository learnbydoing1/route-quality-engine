package com.jeeny.rqe.controller;

import com.jeeny.rqe.dto.TripGenerationRequest;
import com.jeeny.rqe.dto.TripResponse;
import com.jeeny.rqe.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping("/generate")
    public ResponseEntity<TripResponse> generateTrip(@Valid @RequestBody TripGenerationRequest request) {
        TripResponse response = tripService.generateTrip(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<TripResponse>> getAllTrips() {
        return ResponseEntity.ok(tripService.getAllTrips());
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<TripResponse> getTrip(@PathVariable UUID tripId) {
        return ResponseEntity.ok(tripService.getTrip(tripId));
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedDemoData() {
        CompletableFuture.runAsync(() -> tripService.seedDemoData());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "seeding", "message", "Generating 13 trips in background. Refresh in ~30 seconds."));
    }
}
