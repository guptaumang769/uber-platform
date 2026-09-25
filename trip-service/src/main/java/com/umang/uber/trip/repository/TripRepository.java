package com.umang.uber.trip.repository;

import com.umang.uber.trip.entity.Trip;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByRideRequestId(Long rideRequestId);
}
