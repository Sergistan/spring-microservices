package com.utochkin.paymentservice.repositories;

import com.utochkin.paymentservice.models.Payment;
import com.utochkin.paymentservice.models.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
/**
 * Платежи; уникальность (orderUuid+type) на уровне БД — защита от двойной оплаты/возврата.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Единственный платёж заказа по типу (PAYMENT/REFUND) — опора идемпотентности и сверки. */
    Optional<Payment> findByOrderUuidAndType(UUID orderUuid, PaymentType type);
}
