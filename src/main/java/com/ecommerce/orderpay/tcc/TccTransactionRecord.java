package com.ecommerce.orderpay.tcc;

import java.time.Instant;

public class TccTransactionRecord {
    private final long id;
    private final String txId;
    private final long orderId;
    private final String userId;
    private final TccBranch branch;
    private final TccPhase phase;
    private final TccActionResult result;
    private final String payload;
    private final String errorMessage;
    private final Instant createdAt;

    public TccTransactionRecord(
        long id,
        String txId,
        long orderId,
        String userId,
        TccBranch branch,
        TccPhase phase,
        TccActionResult result,
        String payload,
        String errorMessage,
        Instant createdAt
    ) {
        this.id = id;
        this.txId = txId;
        this.orderId = orderId;
        this.userId = userId;
        this.branch = branch;
        this.phase = phase;
        this.result = result;
        this.payload = payload;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getTxId() {
        return txId;
    }

    public long getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }

    public TccBranch getBranch() {
        return branch;
    }

    public TccPhase getPhase() {
        return phase;
    }

    public TccActionResult getResult() {
        return result;
    }

    public String getPayload() {
        return payload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
