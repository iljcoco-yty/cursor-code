package com.ecommerce.orderpay.common.idempotency;

public record BeginResult(BeginResultType type, byte[] cachedBody) {
}
