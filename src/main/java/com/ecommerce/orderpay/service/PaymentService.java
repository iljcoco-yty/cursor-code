package com.ecommerce.orderpay.service;

import com.ecommerce.orderpay.api.dto.PayRequest;
import com.ecommerce.orderpay.api.dto.PayResponse;
import com.ecommerce.orderpay.common.BizException;
import com.ecommerce.orderpay.common.ErrorCode;
import com.ecommerce.orderpay.common.HashUtils;
import com.ecommerce.orderpay.common.idempotency.IdempotentExecutor;
import com.ecommerce.orderpay.common.lock.KeyLockManager;
import com.ecommerce.orderpay.domain.Order;
import com.ecommerce.orderpay.domain.OrderStatus;
import com.ecommerce.orderpay.domain.Payment;
import com.ecommerce.orderpay.leaf.LeafSegmentIdGenerator;
import com.ecommerce.orderpay.repository.OrderRepository;
import com.ecommerce.orderpay.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PaymentService {

    private static final Duration IDEMPOTENT_TTL = Duration.ofMinutes(30);

    private final LeafSegmentIdGenerator idGenerator;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final IdempotentExecutor idempotentExecutor;
    private final KeyLockManager keyLockManager;
    private final ObjectMapper objectMapper;

    public PaymentService(
        LeafSegmentIdGenerator idGenerator,
        OrderRepository orderRepository,
        PaymentRepository paymentRepository,
        IdempotentExecutor idempotentExecutor,
        KeyLockManager keyLockManager,
        ObjectMapper objectMapper
    ) {
        this.idGenerator = idGenerator;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.idempotentExecutor = idempotentExecutor;
        this.keyLockManager = keyLockManager;
        this.objectMapper = objectMapper;
    }

    public PayResponse pay(PayRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BizException(ErrorCode.INVALID_ARGUMENT, "Idempotency-Key不能为空");
        }
        String fingerprint = HashUtils.sha256OfObject(objectMapper, request);
        return idempotentExecutor.execute(
            "payment:pay",
            idempotencyKey,
            fingerprint,
            IDEMPOTENT_TTL,
            PayResponse.class,
            () -> doPay(request)
        );
    }

    private PayResponse doPay(PayRequest request) {
        String lockKey = "pay:order:" + request.orderId();
        ReentrantLock lock = keyLockManager.getLock(lockKey);
        lock.lock();
        try {
            Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在"));

            if (!order.getUserId().equals(request.userId())) {
                throw new BizException(ErrorCode.INVALID_ARGUMENT, "订单用户不匹配");
            }

            Payment paymentByExternal = paymentRepository.findByExternalNo(request.externalNo()).orElse(null);
            if (paymentByExternal != null) {
                return toResponse(paymentByExternal);
            }

            if (order.getStatus() == OrderStatus.PAID) {
                Payment existing = paymentRepository.findByOrderId(order.getOrderId())
                    .orElseThrow(() -> new BizException(ErrorCode.ORDER_STATE_INVALID, "订单已支付但支付单缺失"));
                return toResponse(existing);
            }

            if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                throw new BizException(ErrorCode.ORDER_STATE_INVALID, "当前订单状态不可支付");
            }

            long paymentId = idGenerator.nextId("payment");
            Payment payment = new Payment(
                paymentId,
                order.getOrderId(),
                order.getUserId(),
                request.channel(),
                request.externalNo(),
                order.getAmountCents()
            );

            PaymentRepository.SavePaymentResult saveResult = paymentRepository.saveIfAbsent(payment);
            Payment persisted = saveResult.payment();
            if (persisted == null) {
                throw new BizException(ErrorCode.SYSTEM_ERROR, "支付单写入异常");
            }

            if (saveResult.created()) {
                boolean updated = order.markPaidIfPending();
                if (!updated && order.getStatus() != OrderStatus.PAID) {
                    throw new BizException(ErrorCode.ORDER_STATE_INVALID, "订单状态更新失败");
                }
            }
            return toResponse(persisted);
        } finally {
            lock.unlock();
        }
    }

    private PayResponse toResponse(Payment payment) {
        return new PayResponse(
            payment.getPaymentId(),
            payment.getOrderId(),
            payment.getUserId(),
            payment.getChannel(),
            payment.getExternalNo(),
            payment.getAmountCents(),
            payment.getCreatedAt()
        );
    }
}
