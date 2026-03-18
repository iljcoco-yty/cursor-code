package com.ecommerce.orderpay.common.idempotency;

import com.ecommerce.orderpay.common.BizException;
import com.ecommerce.orderpay.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class IdempotentExecutor {

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public IdempotentExecutor(IdempotencyStore idempotencyStore, ObjectMapper objectMapper) {
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    public <T> T execute(
        String scope,
        String key,
        String fingerprint,
        Duration ttl,
        Class<T> clazz,
        Supplier<T> supplier
    ) {
        BeginResult beginResult = idempotencyStore.begin(scope, key, fingerprint, ttl);
        if (beginResult.type() == BeginResultType.DONE) {
            return deserialize(beginResult.cachedBody(), clazz);
        }
        if (beginResult.type() == BeginResultType.BUSY) {
            throw new BizException(ErrorCode.IDEMPOTENCY_BUSY, "request is processing, please retry");
        }
        if (beginResult.type() == BeginResultType.MISMATCH) {
            throw new BizException(ErrorCode.IDEMPOTENCY_MISMATCH, "idempotency key already used by different request");
        }

        try {
            T result = supplier.get();
            idempotencyStore.complete(scope, key, serialize(result), ttl);
            return result;
        } catch (RuntimeException ex) {
            idempotencyStore.abort(scope, key);
            throw ex;
        }
    }

    private byte[] serialize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "serialize idempotency response failed");
        }
    }

    private <T> T deserialize(byte[] payload, Class<T> clazz) {
        try {
            return objectMapper.readValue(payload, clazz);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "deserialize idempotency response failed");
        }
    }
}
