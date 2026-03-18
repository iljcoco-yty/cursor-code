package com.ecommerce.orderpay.leaf;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemorySegmentAllocRepository implements SegmentAllocRepository {

    private final ConcurrentMap<String, AtomicLong> maxIds = new ConcurrentHashMap<>();

    @Override
    public long allocateMaxId(String bizTag, int step) {
        AtomicLong cursor = maxIds.computeIfAbsent(bizTag, k -> new AtomicLong(0L));
        return cursor.addAndGet(step);
    }
}
