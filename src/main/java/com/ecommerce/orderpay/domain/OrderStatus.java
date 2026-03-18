package com.ecommerce.orderpay.domain;

public enum OrderStatus {
    INIT,
    PENDING_PAYMENT,
    PAYING,
    PAID,
    CANCELLED
}
