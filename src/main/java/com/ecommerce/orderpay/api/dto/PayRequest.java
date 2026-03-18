package com.ecommerce.orderpay.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PayRequest(
    @NotBlank(message = "userId不能为空") String userId,
    @NotNull(message = "orderId不能为空") Long orderId,
    @NotBlank(message = "channel不能为空") String channel,
    @NotBlank(message = "externalNo不能为空") String externalNo
) {
}
