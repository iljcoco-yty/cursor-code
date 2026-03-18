package com.ecommerce.orderpay.leaf;

public interface SegmentAllocRepository {
    long allocateMaxId(String bizTag, int step);
}
