package com.ecommerce.orderpay.common.idempotency;

import java.time.Instant;

public class IdempotencyRecord {
    private final String scope;
    private final String key;
    private final String fingerprint;
    private final IdempotencyState state;
    private final byte[] responseBody;
    private final Instant expireAt;

    public IdempotencyRecord(
        String scope,
        String key,
        String fingerprint,
        IdempotencyState state,
        byte[] responseBody,
        Instant expireAt
    ) {
        this.scope = scope;
        this.key = key;
        this.fingerprint = fingerprint;
        this.state = state;
        this.responseBody = responseBody;
        this.expireAt = expireAt;
    }

    public String getScope() {
        return scope;
    }

    public String getKey() {
        return key;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public IdempotencyState getState() {
        return state;
    }

    public byte[] getResponseBody() {
        return responseBody;
    }

    public Instant getExpireAt() {
        return expireAt;
    }
}
