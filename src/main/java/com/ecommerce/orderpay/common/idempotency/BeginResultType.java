package com.ecommerce.orderpay.common.idempotency;

public enum BeginResultType {
    NEW,
    DONE,
    BUSY,
    MISMATCH
}
