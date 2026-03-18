package com.ecommerce.orderpay.leaf;

import com.ecommerce.orderpay.common.BizException;
import com.ecommerce.orderpay.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LeafSegmentIdGenerator {

    private final SegmentAllocRepository allocRepository;
    private final Map<String, SegmentBuffer> buffers = new ConcurrentHashMap<>();
    private final ExecutorService preloadExecutor = Executors.newFixedThreadPool(2);
    private final int step = 1000;

    public LeafSegmentIdGenerator(SegmentAllocRepository allocRepository) {
        this.allocRepository = allocRepository;
    }

    public long nextId(String bizTag) {
        SegmentBuffer buffer = buffers.computeIfAbsent(bizTag, this::newBuffer);
        return buffer.nextId();
    }

    private SegmentBuffer newBuffer(String bizTag) {
        Segment current = loadSegment(bizTag);
        return new SegmentBuffer(bizTag, current);
    }

    private Segment loadSegment(String bizTag) {
        long max = allocRepository.allocateMaxId(bizTag, step);
        long start = max - step + 1;
        return new Segment(start - 1, max, step);
    }

    private final class SegmentBuffer {
        private final String bizTag;
        private final Object swapLock = new Object();
        private final AtomicInteger preloading = new AtomicInteger(0);
        private volatile Segment current;
        private volatile Segment next;

        private SegmentBuffer(String bizTag, Segment current) {
            this.bizTag = bizTag;
            this.current = current;
        }

        private long nextId() {
            while (true) {
                Segment currentSnapshot = current;
                long candidate = currentSnapshot.value.incrementAndGet();
                if (candidate <= currentSnapshot.max) {
                    long remain = currentSnapshot.max - candidate;
                    if (remain < currentSnapshot.step / 10) {
                        tryPreload();
                    }
                    return candidate;
                }
                refill();
            }
        }

        private void tryPreload() {
            if (!preloading.compareAndSet(0, 1)) {
                return;
            }
            preloadExecutor.submit(() -> {
                try {
                    next = loadSegment(bizTag);
                } finally {
                    preloading.set(0);
                }
            });
        }

        private void refill() {
            synchronized (swapLock) {
                if (current.value.get() < current.max) {
                    return;
                }
                if (next != null) {
                    current = next;
                    next = null;
                    return;
                }
                current = loadSegment(bizTag);
            }
        }
    }

    private static final class Segment {
        private final AtomicLong value;
        private final long max;
        private final int step;

        private Segment(long initialValue, long max, int step) {
            if (initialValue >= max) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "invalid leaf segment");
            }
            this.value = new AtomicLong(initialValue);
            this.max = max;
            this.step = step;
        }
    }
}
