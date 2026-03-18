package com.ecommerce.orderpay.domain;

import java.time.Instant;

public class Payment {
    private final long paymentId;
    private final long orderId;
    private final String userId;
    private final String channel;
    private final String externalNo;
    private final long amountCents;
    private final Instant createdAt;

    public Payment(long paymentId, long orderId, String userId, String channel, String externalNo, long amountCents) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.userId = userId;
        this.channel = channel;
        this.externalNo = externalNo;
        this.amountCents = amountCents;
        this.createdAt = Instant.now();
    }

    public long getPaymentId() {
        return paymentId;
    }

    public long getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }

    public String getChannel() {
        return channel;
    }

    public String getExternalNo() {
        return externalNo;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
