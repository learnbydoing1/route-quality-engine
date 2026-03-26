package com.jeeny.rqe.repository;

import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.model.PointType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GpsPointRepository extends JpaRepository<GpsPoint, UUID> {

    List<GpsPoint> findByTripIdAndPointTypeOrderBySequenceIndex(UUID tripId, PointType pointType);

    List<GpsPoint> findByTripIdOrderBySequenceIndex(UUID tripId);

    void deleteByTripIdAndPointType(UUID tripId, PointType pointType);
}
