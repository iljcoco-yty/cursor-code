package com.ecommerce.orderpay.service;

import com.ecommerce.orderpay.api.dto.OrderItemDto;
import com.ecommerce.orderpay.api.dto.PlaceOrderRequest;
import com.ecommerce.orderpay.api.dto.PlaceOrderResponse;
import com.ecommerce.orderpay.common.BizException;
import com.ecommerce.orderpay.common.ErrorCode;
import com.ecommerce.orderpay.common.HashUtils;
import com.ecommerce.orderpay.common.guard.DuplicateGuard;
import com.ecommerce.orderpay.common.idempotency.IdempotentExecutor;
import com.ecommerce.orderpay.domain.Order;
import com.ecommerce.orderpay.domain.OrderItem;
import com.ecommerce.orderpay.leaf.LeafSegmentIdGenerator;
import com.ecommerce.orderpay.repository.OrderRepository;
import com.ecommerce.orderpay.tcc.TccCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class OrderService {

    private static final Duration IDEMPOTENT_TTL = Duration.ofMinutes(30);
    private static final Duration DEDUP_LOCK_TTL = Duration.ofSeconds(5);

    private final LeafSegmentIdGenerator idGenerator;
    private final TccCoordinator tccCoordinator;
    private final OrderRepository orderRepository;
    private final DuplicateGuard duplicateGuard;
    private final IdempotentExecutor idempotentExecutor;
    private final ObjectMapper objectMapper;

    public OrderService(
        LeafSegmentIdGenerator idGenerator,
        TccCoordinator tccCoordinator,
        OrderRepository orderRepository,
        DuplicateGuard duplicateGuard,
        IdempotentExecutor idempotentExecutor,
        ObjectMapper objectMapper
    ) {
        this.idGenerator = idGenerator;
        this.tccCoordinator = tccCoordinator;
        this.orderRepository = orderRepository;
        this.duplicateGuard = duplicateGuard;
        this.idempotentExecutor = idempotentExecutor;
        this.objectMapper = objectMapper;
    }

    public PlaceOrderResponse placeOrder(PlaceOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "Idempotency-Key不能为空");
        }
        String fingerprint = HashUtils.sha256OfObject(objectMapper, request);
        return idempotentExecutor.execute(
            "order:create",
            idempotencyKey,
            fingerprint,
            IDEMPOTENT_TTL,
            PlaceOrderResponse.class,
            () -> doPlaceOrder(request)
        );
    }

    private PlaceOrderResponse doPlaceOrder(PlaceOrderRequest request) {
        String businessKey = businessKey(request.userId(), request.checkoutToken());
        String lockKey = "order:place:" + businessKey;
        if (!duplicateGuard.tryLock(lockKey, DEDUP_LOCK_TTL)) {
            throw new BizException(ErrorCode.DUPLICATE_ORDER, "重复下单，请勿频繁提交");
        }

        try {
            return orderRepository.findByBusinessKey(businessKey)
                .map(this::toResponse)
                .orElseGet(() -> createNewOrder(request, businessKey));
        } finally {
            duplicateGuard.unlock(lockKey);
        }
    }

    private PlaceOrderResponse createNewOrder(PlaceOrderRequest request, String businessKey) {
        long orderId = idGenerator.nextId("order");
        String txId = "ORDER-TCC-" + orderId;
        List<OrderItem> items = request.items().stream()
            .map(this::toDomainItem)
            .toList();

        tccCoordinator.executePlaceOrder(txId, request.userId(), items, request.couponId());

        Order order = new Order(
            orderId,
            request.userId(),
            items,
            request.couponId(),
            request.amountCents(),
            businessKey
        );

        OrderRepository.SaveOrderResult saveResult = orderRepository.saveIfBusinessAbsent(order);
        return toResponse(saveResult.order());
    }

    private OrderItem toDomainItem(OrderItemDto dto) {
        return new OrderItem(dto.skuId(), dto.quantity());
    }

    private String businessKey(String userId, String checkoutToken) {
        return userId + ":" + checkoutToken;
    }

    private PlaceOrderResponse toResponse(Order order) {
        return new PlaceOrderResponse(
            order.getOrderId(),
            order.getUserId(),
            order.getAmountCents(),
            order.getCouponId(),
            order.getStatus(),
            order.getCreatedAt()
        );
    }
}
