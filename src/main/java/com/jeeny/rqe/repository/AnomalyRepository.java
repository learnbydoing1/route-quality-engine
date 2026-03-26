package com.jeeny.rqe.repository;

import com.jeeny.rqe.model.Anomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnomalyRepository extends JpaRepository<Anomaly, UUID> {

    List<Anomaly> findByTripId(UUID tripId);

    void deleteByTripId(UUID tripId);
}
