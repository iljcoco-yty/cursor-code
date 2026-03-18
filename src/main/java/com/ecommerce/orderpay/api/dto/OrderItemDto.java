package com.ecommerce.orderpay.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderItemDto(
    @NotBlank(message = "skuId不能为空") String skuId,
    @Min(value = 1, message = "quantity必须大于0") int quantity
) {
}
