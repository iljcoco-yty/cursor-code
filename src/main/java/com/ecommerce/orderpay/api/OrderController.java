package com.ecommerce.orderpay.api;

import com.ecommerce.orderpay.api.dto.PlaceOrderRequest;
import com.ecommerce.orderpay.api.dto.PlaceOrderResponse;
import com.ecommerce.orderpay.common.ApiResponse;
import com.ecommerce.orderpay.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<PlaceOrderResponse> placeOrder(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PlaceOrderRequest request
    ) {
        PlaceOrderResponse response = orderService.placeOrder(request, idempotencyKey);
        return ApiResponse.success(response);
    }
}
