package com.ecommerce.orderpay.tcc;

import com.ecommerce.orderpay.domain.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TccCoordinator {

    private final InventoryTccService inventoryTccService;
    private final CouponTccService couponTccService;

    public TccCoordinator(InventoryTccService inventoryTccService, CouponTccService couponTccService) {
        this.inventoryTccService = inventoryTccService;
        this.couponTccService = couponTccService;
    }

    public void tryPlaceOrder(String txId, long orderId, String userId, List<OrderItem> items, String couponId) {
        boolean inventoryTried = false;
        boolean couponTried = false;
        try {
            inventoryTccService.tryReserve(txId, orderId, userId, items);
            inventoryTried = true;

            if (couponId != null && !couponId.isBlank()) {
                couponTccService.tryReserve(txId, orderId, userId, couponId);
                couponTried = true;
            }
        } catch (RuntimeException ex) {
            if (couponTried) {
                couponTccService.cancel(txId, orderId, userId, couponId);
            }
            if (inventoryTried) {
                inventoryTccService.cancel(txId, orderId, userId);
            }
            throw ex;
        }
    }

    public void confirmPlaceOrder(String txId, long orderId, String userId, String couponId) {
        inventoryTccService.confirm(txId, orderId, userId);
        if (couponId != null && !couponId.isBlank()) {
            couponTccService.confirm(txId, orderId, userId, couponId);
        }
    }

    public void cancelPlaceOrder(String txId, long orderId, String userId, String couponId) {
        if (couponId != null && !couponId.isBlank()) {
            couponTccService.cancel(txId, orderId, userId, couponId);
        }
        inventoryTccService.cancel(txId, orderId, userId);
    }
}
