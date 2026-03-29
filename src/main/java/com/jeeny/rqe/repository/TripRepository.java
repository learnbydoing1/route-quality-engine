package com.jeeny.rqe.repository;

import com.jeeny.rqe.model.ReviewStatus;
import com.jeeny.rqe.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    List<Trip> findByReviewStatus(ReviewStatus status);

    List<Trip> findAllByOrderByCreatedAtDesc();

    List<Trip> findByDriverIdAndCreatedAtBetween(String driverId, Instant from, Instant to);

    List<Trip> findByDriverId(String driverId);
}
