package com.ecommerce.orderpay.tcc;

import com.ecommerce.orderpay.common.BizException;
import com.ecommerce.orderpay.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CouponTccService {

    private final Map<String, CouponRecord> coupons = new ConcurrentHashMap<>();
    private final TccTransactionRepository transactionRepository;

    public CouponTccService(TccTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
        coupons.put("COUPON-1", new CouponRecord("COUPON-1", "U1001", CouponState.AVAILABLE));
        coupons.put("COUPON-2", new CouponRecord("COUPON-2", "U1002", CouponState.AVAILABLE));
    }

    public synchronized void tryReserve(String txId, long orderId, String userId, String couponId) {
        if (couponId == null || couponId.isBlank()) {
            return;
        }
        if (transactionRepository.hasSuccess(txId, TccBranch.COUPON, TccPhase.TRY)) {
            return;
        }
        try {
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
            transactionRepository.recordSuccess(txId, orderId, userId, TccBranch.COUPON, TccPhase.TRY, couponId);
        } catch (RuntimeException ex) {
            transactionRepository.recordFailure(
                txId,
                orderId,
                userId,
                TccBranch.COUPON,
                TccPhase.TRY,
                couponId,
                ex.getMessage()
            );
            throw ex;
        }
    }

    public synchronized void confirm(String txId, long orderId, String userId, String couponId) {
        if (couponId == null || couponId.isBlank()) {
            return;
        }
        if (transactionRepository.hasSuccess(txId, TccBranch.COUPON, TccPhase.CONFIRM)) {
            return;
        }
        TccTransactionRecord tryRecord = transactionRepository
            .findLatestSuccess(txId, TccBranch.COUPON, TccPhase.TRY)
            .orElse(null);
        if (tryRecord == null) {
            throw new BizException(ErrorCode.ORDER_STATE_INVALID, "coupon tcc try not found");
        }
        CouponRecord coupon = coupons.get(tryRecord.getPayload());
        if (coupon == null) {
            return;
        }
        String triedCouponId = tryRecord.getPayload();
        coupons.put(triedCouponId, coupon.withState(CouponState.USED));
        transactionRepository.recordSuccess(txId, orderId, userId, TccBranch.COUPON, TccPhase.CONFIRM, triedCouponId);
    }

    public synchronized void cancel(String txId, long orderId, String userId, String couponId) {
        if (couponId == null || couponId.isBlank()) {
            return;
        }
        if (transactionRepository.hasSuccess(txId, TccBranch.COUPON, TccPhase.CANCEL)) {
            return;
        }
        TccTransactionRecord tryRecord = transactionRepository
            .findLatestSuccess(txId, TccBranch.COUPON, TccPhase.TRY)
            .orElse(null);
        if (tryRecord == null) {
            transactionRepository.recordSuccess(txId, orderId, userId, TccBranch.COUPON, TccPhase.CANCEL, "noop");
            return;
        }
        String triedCouponId = tryRecord.getPayload();
        CouponRecord coupon = coupons.get(triedCouponId);
        if (coupon == null) {
            transactionRepository.recordSuccess(txId, orderId, userId, TccBranch.COUPON, TccPhase.CANCEL, "noop");
            return;
        }
        if (coupon.state() == CouponState.FROZEN) {
            coupons.put(triedCouponId, coupon.withState(CouponState.AVAILABLE));
        }
        transactionRepository.recordSuccess(txId, orderId, userId, TccBranch.COUPON, TccPhase.CANCEL, triedCouponId);
    }

    private record CouponRecord(String couponId, String userId, CouponState state) {
        private CouponRecord withState(CouponState newState) {
            return new CouponRecord(couponId, userId, newState);
        }
    }
}
