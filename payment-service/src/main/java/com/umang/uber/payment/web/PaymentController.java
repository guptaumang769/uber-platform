package com.umang.uber.payment.web;

import com.umang.uber.common.api.ApiResponse;
import com.umang.uber.payment.service.PaymentService;
import com.umang.uber.payment.web.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/trip/{tripId}")
    public ApiResponse<PaymentResponse> byTrip(@PathVariable Long tripId) {
        return ApiResponse.ok(PaymentResponse.from(paymentService.getByTripId(tripId)));
    }
}
