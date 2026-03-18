package com.ecommerce.orderpay.api;

import com.ecommerce.orderpay.api.dto.PayRequest;
import com.ecommerce.orderpay.api.dto.PayResponse;
import com.ecommerce.orderpay.common.ApiResponse;
import com.ecommerce.orderpay.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ApiResponse<PayResponse> pay(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PayRequest request
    ) {
        PayResponse response = paymentService.pay(request, idempotencyKey);
        return ApiResponse.success(response);
    }
}
