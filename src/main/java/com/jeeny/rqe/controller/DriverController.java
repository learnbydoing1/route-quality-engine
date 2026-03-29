package com.jeeny.rqe.controller;

import com.jeeny.rqe.dto.DriverSummary;
import com.jeeny.rqe.dto.ReasonCodeSummary;
import com.jeeny.rqe.service.DriverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @GetMapping("/{driverId}/summary")
    public ResponseEntity<DriverSummary> getDriverSummary(
            @PathVariable String driverId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        if (to == null) to = Instant.now();
        if (from == null) from = to.minus(30, ChronoUnit.DAYS);

        return ResponseEntity.ok(driverService.getDriverSummary(driverId, from, to));
    }

    @GetMapping("/{driverId}/reason-code-summary")
    public ResponseEntity<ReasonCodeSummary> getReasonCodeSummary(
            @PathVariable String driverId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        if (to == null) to = Instant.now();
        if (from == null) from = to.minus(30, ChronoUnit.DAYS);

        return ResponseEntity.ok(driverService.getReasonCodeSummary(driverId, from, to));
    }
}
