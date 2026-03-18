package com.ecommerce.orderpay.tcc;

import com.ecommerce.orderpay.common.BizException;
import com.ecommerce.orderpay.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CouponTccService {

    private final Map<String, CouponRecord> coupons = new ConcurrentHashMap<>();
    private final Map<String, String> frozenCouponByTx = new ConcurrentHashMap<>();

    public CouponTccService() {
        coupons.put("COUPON-1", new CouponRecord("COUPON-1", "U1001", CouponState.AVAILABLE));
        coupons.put("COUPON-2", new CouponRecord("COUPON-2", "U1002", CouponState.AVAILABLE));
    }

    public synchronized void tryReserve(String txId, String userId, String couponId) {
        if (couponId == null || couponId.isBlank()) {
            return;
        }
        String frozenCoupon = frozenCouponByTx.get(txId);
        if (couponId.equals(frozenCoupon)) {
            return;
        }
        CouponRecord coupon = coupons.get(couponId);
        if (coupon == null) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "coupon not found");
        }
        if (!coupon.userId().equals(userId)) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "coupon user mismatch");
        }
        if (coupon.state() != CouponState.AVAILABLE) {
            throw new BizException(ErrorCode.COUPON_UNAVAILABLE, "coupon is unavailable");
        }
        coupons.put(couponId, coupon.withState(CouponState.FROZEN));
        frozenCouponByTx.put(txId, couponId);
    }

    public synchronized void confirm(String txId) {
        String couponId = frozenCouponByTx.remove(txId);
        if (couponId == null) {
            return;
        }
        CouponRecord coupon = coupons.get(couponId);
        if (coupon == null) {
            return;
        }
        coupons.put(couponId, coupon.withState(CouponState.USED));
    }

    public synchronized void cancel(String txId) {
        String couponId = frozenCouponByTx.remove(txId);
        if (couponId == null) {
            return;
        }
        CouponRecord coupon = coupons.get(couponId);
        if (coupon == null) {
            return;
        }
        if (coupon.state() == CouponState.FROZEN) {
            coupons.put(couponId, coupon.withState(CouponState.AVAILABLE));
        }
    }

    private record CouponRecord(String couponId, String userId, CouponState state) {
        private CouponRecord withState(CouponState newState) {
            return new CouponRecord(couponId, userId, newState);
        }
    }
}
