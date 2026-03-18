package com.ecommerce.orderpay.common.guard;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryDuplicateGuard implements DuplicateGuard {

    private final Map<String, Instant> lockExpire = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String key, Duration ttl) {
        Instant now = Instant.now();
        Instant newExpire = now.plus(ttl);

        while (true) {
            Instant existing = lockExpire.get(key);
            if (existing != null && existing.isAfter(now)) {
                return false;
            }
            if (existing == null) {
                if (lockExpire.putIfAbsent(key, newExpire) == null) {
                    return true;
                }
            } else {
                if (lockExpire.replace(key, existing, newExpire)) {
                    return true;
                }
            }
        }
    }

    @Override
    public void unlock(String key) {
        lockExpire.remove(key);
    }
}
