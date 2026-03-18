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

    public void executePlaceOrder(String txId, String userId, List<OrderItem> items, String couponId) {
        boolean inventoryTried = false;
        boolean couponTried = false;
        try {
            inventoryTccService.tryReserve(txId, items);
            inventoryTried = true;

            if (couponId != null && !couponId.isBlank()) {
                couponTccService.tryReserve(txId, userId, couponId);
                couponTried = true;
            }

            inventoryTccService.confirm(txId);
            if (couponTried) {
                couponTccService.confirm(txId);
            }
        } catch (RuntimeException ex) {
            if (couponTried) {
                couponTccService.cancel(txId);
            }
            if (inventoryTried) {
                inventoryTccService.cancel(txId);
            }
            throw ex;
        }
    }
}
