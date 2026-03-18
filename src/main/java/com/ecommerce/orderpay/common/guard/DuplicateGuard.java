package com.ecommerce.orderpay.common.guard;

import java.time.Duration;

public interface DuplicateGuard {
    boolean tryLock(String key, Duration ttl);

    void unlock(String key);
}
