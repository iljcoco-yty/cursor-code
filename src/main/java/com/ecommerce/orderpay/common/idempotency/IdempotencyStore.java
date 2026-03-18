package com.ecommerce.orderpay.common.idempotency;

import java.time.Duration;

public interface IdempotencyStore {
    BeginResult begin(String scope, String key, String fingerprint, Duration ttl);

    void complete(String scope, String key, byte[] responseBody, Duration ttl);

    void abort(String scope, String key);
}
