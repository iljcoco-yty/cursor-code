package com.ecommerce.orderpay.repository;

import com.ecommerce.orderpay.domain.Payment;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class PaymentRepository {

    private final ConcurrentMap<Long, Payment> paymentByOrderId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> orderIdByExternalNo = new ConcurrentHashMap<>();

    public Optional<Payment> findByOrderId(long orderId) {
        return Optional.ofNullable(paymentByOrderId.get(orderId));
    }

    public Optional<Payment> findByExternalNo(String externalNo) {
        Long orderId = orderIdByExternalNo.get(externalNo);
        if (orderId == null) {
            return Optional.empty();
        }
        return findByOrderId(orderId);
    }

    public SavePaymentResult saveIfAbsent(Payment payment) {
        Payment existingByOrder = paymentByOrderId.putIfAbsent(payment.getOrderId(), payment);
        if (existingByOrder != null) {
            return SavePaymentResult.duplicate(existingByOrder);
        }
        Long previous = orderIdByExternalNo.putIfAbsent(payment.getExternalNo(), payment.getOrderId());
        if (previous != null && previous != payment.getOrderId()) {
            Payment rollback = paymentByOrderId.remove(payment.getOrderId());
            return SavePaymentResult.duplicate(rollback);
        }
        return SavePaymentResult.created(payment);
    }

    public record SavePaymentResult(boolean created, Payment payment) {
        public static SavePaymentResult created(Payment payment) {
            return new SavePaymentResult(true, payment);
        }

        public static SavePaymentResult duplicate(Payment payment) {
            return new SavePaymentResult(false, payment);
        }
    }
}
