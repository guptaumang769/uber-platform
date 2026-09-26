package com.umang.uber.payment.repository;

import com.umang.uber.payment.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTripId(Long tripId);
}
