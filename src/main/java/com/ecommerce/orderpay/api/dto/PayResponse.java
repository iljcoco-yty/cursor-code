package com.ecommerce.orderpay.api.dto;

import java.time.Instant;

public record PayResponse(
    long paymentId,
    long orderId,
    String userId,
    String channel,
    String externalNo,
    long amountCents,
    Instant paidAt
) {
}
