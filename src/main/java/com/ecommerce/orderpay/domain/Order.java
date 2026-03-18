package com.ecommerce.orderpay.domain;

import java.time.Instant;
import java.util.List;

public class Order {
    private final long orderId;
    private final String userId;
    private final List<OrderItem> items;
    private final String couponId;
    private final long amountCents;
    private final String businessKey;
    private volatile OrderStatus status;
    private final Instant createdAt;
    private volatile Instant updatedAt;

    public Order(
        long orderId,
        String userId,
        List<OrderItem> items,
        String couponId,
        long amountCents,
        String businessKey
    ) {
        this.orderId = orderId;
        this.userId = userId;
        this.items = List.copyOf(items);
        this.couponId = couponId;
        this.amountCents = amountCents;
        this.businessKey = businessKey;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public long getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public String getCouponId() {
        return couponId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getBusinessKey() {
        return businessKey;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public synchronized boolean markPaidIfPending() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        status = OrderStatus.PAID;
        updatedAt = Instant.now();
        return true;
    }
}
