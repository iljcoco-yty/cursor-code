package com.ecommerce.orderpay.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlaceOrderRequest(
    @NotBlank(message = "userId不能为空") String userId,
    @NotBlank(message = "checkoutToken不能为空") String checkoutToken,
    @NotEmpty(message = "items不能为空") List<@Valid OrderItemDto> items,
    String couponId,
    @Min(value = 1, message = "amountCents必须大于0") long amountCents
) {
}
