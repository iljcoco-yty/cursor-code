package com.ecommerce.orderpay.domain;

import com.ecommerce.orderpay.common.BizException;
import com.ecommerce.orderpay.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class OrderStateMachine {

    private final Map<OrderStatus, Map<OrderEvent, OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);

    public OrderStateMachine() {
        add(OrderStatus.INIT, OrderEvent.PLACE_TRY_SUCCESS, OrderStatus.PENDING_PAYMENT);
        add(OrderStatus.PENDING_PAYMENT, OrderEvent.PAYMENT_START, OrderStatus.PAYING);
        add(OrderStatus.PAYING, OrderEvent.PAYMENT_SUCCESS, OrderStatus.PAID);
        add(OrderStatus.PENDING_PAYMENT, OrderEvent.CANCEL, OrderStatus.CANCELLED);
        add(OrderStatus.PAYING, OrderEvent.CANCEL, OrderStatus.CANCELLED);
    }

    public OrderStatus next(OrderStatus current, OrderEvent event) {
        Map<OrderEvent, OrderStatus> eventMap = transitions.get(current);
        if (eventMap == null || !eventMap.containsKey(event)) {
            throw new BizException(
                ErrorCode.ORDER_STATE_INVALID,
                "invalid order state transition: " + current + " --" + event + "-->"
            );
        }
        return eventMap.get(event);
    }

    private void add(OrderStatus from, OrderEvent event, OrderStatus to) {
        transitions.computeIfAbsent(from, k -> new EnumMap<>(OrderEvent.class)).put(event, to);
    }
}
