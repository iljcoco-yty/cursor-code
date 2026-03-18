package com.ecommerce.orderpay.tcc;

import com.ecommerce.orderpay.common.BizException;
import com.ecommerce.orderpay.common.ErrorCode;
import com.ecommerce.orderpay.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryTccService {

    private final Map<String, Integer> availableStock = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> frozenByTx = new ConcurrentHashMap<>();

    public InventoryTccService() {
        availableStock.put("SKU-1", 10_000);
        availableStock.put("SKU-2", 10_000);
        availableStock.put("SKU-3", 10_000);
    }

    public synchronized void tryReserve(String txId, List<OrderItem> items) {
        if (frozenByTx.containsKey(txId)) {
            return;
        }

        for (OrderItem item : items) {
            if (item.quantity() <= 0) {
                throw new BizException(ErrorCode.INVALID_ARGUMENT, "quantity should be positive");
            }
            int stock = availableStock.getOrDefault(item.skuId(), 0);
            if (stock < item.quantity()) {
                throw new BizException(ErrorCode.INSUFFICIENT_STOCK, "insufficient stock for sku=" + item.skuId());
            }
        }

        Map<String, Integer> frozen = new HashMap<>();
        for (OrderItem item : items) {
            int stock = availableStock.getOrDefault(item.skuId(), 0);
            availableStock.put(item.skuId(), stock - item.quantity());
            frozen.merge(item.skuId(), item.quantity(), Integer::sum);
        }
        frozenByTx.put(txId, frozen);
    }

    public synchronized void confirm(String txId) {
        frozenByTx.remove(txId);
    }

    public synchronized void cancel(String txId) {
        Map<String, Integer> frozen = frozenByTx.remove(txId);
        if (frozen == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : frozen.entrySet()) {
            availableStock.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }
}
