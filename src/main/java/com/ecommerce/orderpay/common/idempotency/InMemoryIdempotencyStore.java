package com.ecommerce.orderpay.common.idempotency;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();
    private final ScheduledExecutorService gcExecutor = Executors.newSingleThreadScheduledExecutor();

    public InMemoryIdempotencyStore() {
        gcExecutor.scheduleAtFixedRate(this::gc, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    public BeginResult begin(String scope, String key, String fingerprint, Duration ttl) {
        String compositeKey = composite(scope, key);
        Instant now = Instant.now();

        while (true) {
            IdempotencyRecord existing = records.get(compositeKey);
            if (existing != null && existing.getExpireAt().isAfter(now)) {
                if (!existing.getFingerprint().equals(fingerprint)) {
                    return new BeginResult(BeginResultType.MISMATCH, null);
                }
                if (existing.getState() == IdempotencyState.DONE) {
                    return new BeginResult(BeginResultType.DONE, existing.getResponseBody());
                }
                return new BeginResult(BeginResultType.BUSY, null);
            }

            IdempotencyRecord fresh = new IdempotencyRecord(
                scope,
                key,
                fingerprint,
                IdempotencyState.PROCESSING,
                null,
                now.plus(ttl)
            );

            if (existing == null) {
                if (records.putIfAbsent(compositeKey, fresh) == null) {
                    return new BeginResult(BeginResultType.NEW, null);
                }
            } else {
                if (records.replace(compositeKey, existing, fresh)) {
                    return new BeginResult(BeginResultType.NEW, null);
                }
            }
        }
    }

    @Override
    public void complete(String scope, String key, byte[] responseBody, Duration ttl) {
        String compositeKey = composite(scope, key);
        IdempotencyRecord existing = records.get(compositeKey);
        if (existing == null) {
            return;
        }
        IdempotencyRecord done = new IdempotencyRecord(
            existing.getScope(),
            existing.getKey(),
            existing.getFingerprint(),
            IdempotencyState.DONE,
            responseBody,
            Instant.now().plus(ttl)
        );
        records.put(compositeKey, done);
    }

    @Override
    public void abort(String scope, String key) {
        records.remove(composite(scope, key));
    }

    private void gc() {
        Instant now = Instant.now();
        records.entrySet().removeIf(e -> e.getValue().getExpireAt().isBefore(now));
    }

    private String composite(String scope, String key) {
        return scope + ":" + key;
    }
}
