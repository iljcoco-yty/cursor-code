package com.ecommerce.orderpay.repository;

import com.ecommerce.orderpay.domain.Order;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class OrderRepository {

    private final ConcurrentMap<Long, Order> ordersById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> orderIdByBusinessKey = new ConcurrentHashMap<>();

    public Optional<Order> findById(long orderId) {
        return Optional.ofNullable(ordersById.get(orderId));
    }

    public Optional<Order> findByBusinessKey(String businessKey) {
        Long id = orderIdByBusinessKey.get(businessKey);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ordersById.get(id));
    }

    public SaveOrderResult saveIfBusinessAbsent(Order order) {
        Long existingId = orderIdByBusinessKey.putIfAbsent(order.getBusinessKey(), order.getOrderId());
        if (existingId != null) {
            Order existingOrder = ordersById.get(existingId);
            return SaveOrderResult.duplicate(existingOrder);
        }
        ordersById.put(order.getOrderId(), order);
        return SaveOrderResult.created(order);
    }

    public record SaveOrderResult(boolean created, Order order) {
        public static SaveOrderResult created(Order order) {
            return new SaveOrderResult(true, order);
        }

        public static SaveOrderResult duplicate(Order existing) {
            return new SaveOrderResult(false, existing);
        }
    }
}
