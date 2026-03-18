package com.ecommerce.orderpay.api.dto;

import com.ecommerce.orderpay.domain.OrderStatus;

import java.time.Instant;

public record PlaceOrderResponse(
    long orderId,
    String userId,
    long amountCents,
    String couponId,
    OrderStatus status,
    Instant createdAt
) {
}
