package com.jeeny.rqe.controller;

import com.jeeny.rqe.dto.RouteQualityReport;
import com.jeeny.rqe.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/route-quality")
@RequiredArgsConstructor
public class RouteQualityController {

    private final TripService tripService;

    @GetMapping("/report/{tripId}")
    public ResponseEntity<RouteQualityReport> getReport(@PathVariable UUID tripId) {
        RouteQualityReport report = tripService.buildReport(tripId);
        return ResponseEntity.ok(report);
    }
}
