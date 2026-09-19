package com.umang.uber.rider.repository;

import com.umang.uber.rider.entity.Rider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiderRepository extends JpaRepository<Rider, Long> {
}
