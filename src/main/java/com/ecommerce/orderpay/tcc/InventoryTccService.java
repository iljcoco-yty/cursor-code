package com.ecommerce.orderpay.tcc;

import com.ecommerce.orderpay.common.BizException;
import com.ecommerce.orderpay.common.ErrorCode;
import com.ecommerce.orderpay.domain.OrderItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryTccService {

    private final Map<String, Integer> availableStock = new ConcurrentHashMap<>();
    private final TccTransactionRepository transactionRepository;

    public InventoryTccService(TccTransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
        availableStock.put("SKU-1", 10_000);
        availableStock.put("SKU-2", 10_000);
        availableStock.put("SKU-3", 10_000);
    }

    public synchronized void tryReserve(String txId, long orderId, String userId, List<OrderItem> items) {
        if (transactionRepository.hasSuccess(txId, TccBranch.INVENTORY, TccPhase.TRY)) {
            return;
        }
        String payload = encodeItems(items);
        try {
            for (OrderItem item : items) {
                if (item.quantity() <= 0) {
                    throw new BizException(ErrorCode.INVALID_ARGUMENT, "quantity should be positive");
                }
                int stock = availableStock.getOrDefault(item.skuId(), 0);
                if (stock < item.quantity()) {
                    throw new BizException(ErrorCode.INSUFFICIENT_STOCK, "insufficient stock for sku=" + item.skuId());
                }
            }

            for (OrderItem item : items) {
                int stock = availableStock.getOrDefault(item.skuId(), 0);
                availableStock.put(item.skuId(), stock - item.quantity());
            }
            transactionRepository.recordSuccess(txId, orderId, userId, TccBranch.INVENTORY, TccPhase.TRY, payload);
        } catch (RuntimeException ex) {
            transactionRepository.recordFailure(
                txId,
                orderId,
                userId,
                TccBranch.INVENTORY,
                TccPhase.TRY,
                payload,
                ex.getMessage()
            );
            throw ex;
        }
    }

    public synchronized void confirm(String txId, long orderId, String userId) {
        if (transactionRepository.hasSuccess(txId, TccBranch.INVENTORY, TccPhase.CONFIRM)) {
            return;
        }
        transactionRepository.recordSuccess(txId, orderId, userId, TccBranch.INVENTORY, TccPhase.CONFIRM, "confirmed");
    }

    public synchronized void cancel(String txId, long orderId, String userId) {
        if (transactionRepository.hasSuccess(txId, TccBranch.INVENTORY, TccPhase.CANCEL)) {
            return;
        }
        TccTransactionRecord tryRecord = transactionRepository
            .findLatestSuccess(txId, TccBranch.INVENTORY, TccPhase.TRY)
            .orElse(null);
        if (tryRecord != null) {
            Map<String, Integer> reserved = decodeItems(tryRecord.getPayload());
            for (Map.Entry<String, Integer> entry : reserved.entrySet()) {
                availableStock.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        transactionRepository.recordSuccess(txId, orderId, userId, TccBranch.INVENTORY, TccPhase.CANCEL, "cancelled");
    }

    private String encodeItems(List<OrderItem> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append(item.skuId()).append(":").append(item.quantity());
        }
        return sb.toString();
    }

    private Map<String, Integer> decodeItems(String payload) {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        if (payload == null || payload.isBlank()) {
            return result;
        }
        String[] skuPairs = payload.split(",");
        for (String pair : skuPairs) {
            String[] parts = pair.split(":");
            if (parts.length != 2) {
                continue;
            }
            String sku = parts[0];
            int qty = Integer.parseInt(parts[1]);
            result.merge(sku, qty, Integer::sum);
        }
        return result;
    }
}
