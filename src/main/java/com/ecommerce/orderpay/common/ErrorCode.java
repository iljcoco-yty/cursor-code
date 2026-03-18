package com.ecommerce.orderpay.common;

public enum ErrorCode {
    INVALID_ARGUMENT(1001),
    DUPLICATE_ORDER(1002),
    IDEMPOTENCY_BUSY(1003),
    IDEMPOTENCY_MISMATCH(1004),
    ORDER_NOT_FOUND(1005),
    ORDER_STATE_INVALID(1006),
    INSUFFICIENT_STOCK(1007),
    COUPON_UNAVAILABLE(1008),
    SYSTEM_ERROR(1999);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
