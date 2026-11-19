package com.utochkin.paymentservice.services;

import com.utochkin.paymentservice.exceptions.CardNumberNotFoundException;
import com.utochkin.paymentservice.dto.OrderPaymentsResponse;
import com.utochkin.paymentservice.models.Payment;
import com.utochkin.paymentservice.models.PaymentType;
import com.utochkin.paymentservice.dto.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.repositories.PaymentRepository;
import com.utochkin.paymentservice.web.AccountRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Фасад платёжного API: делегирует транзакционные операции PaymentTxService (pay/refund)
 * и отвечает на сверку getOrderPayments — «была ли оплата/возврат по заказу и какой paymentId».
 * getOrderPayments использует ReconciliationJob в order-service для спасения застрявших заказов.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class PaymentService {

    private final PaymentTxService paymentTxService;
    private final PaymentRepository paymentRepository;

    /** Сводка платежей заказа для сверки (ReconciliationJob): paid/refunded/paymentId. */
    public OrderPaymentsResponse getOrderPayments(UUID orderUuid) {
        Optional<Payment> payment = paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT);
        boolean refunded = paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.REFUND).isPresent();
        boolean paid = payment.map(p -> p.getStatus() == Status.SUCCESS).orElse(false);
        UUID paymentId = payment.map(Payment::getPaymentId).orElse(null);
        log.info("PaymentService: сводка платежей по заказу {}: paid={}, refunded={}", orderUuid, paid, refunded);
        return new OrderPaymentsResponse(orderUuid, paid, refunded, paymentId);
    }

    /**
     * Идемпотентная оплата: платёж по заказу уже есть -> вернуть сохранённый результат;
     * гонка (двое прошли проверку одновременно) -> уникальный индекс в PaymentTxService
     * приводит к DataIntegrityViolation -> возвращаем результат «победителя». Деньги
     * списываются не более одного раза при любом числе повторов.
     */
    public PaymentResponse paymentOrder(AccountRequest accountRequest) {
        log.info("PaymentService: попытка списания {} с карты {} по заказу {}",
                accountRequest.getTotalAmount(),
                PaymentTxService.maskCard(accountRequest.getCardNumber()),
                accountRequest.getOrderUuid());

        Optional<Payment> existing = paymentRepository.findByOrderUuidAndType(accountRequest.getOrderUuid(), PaymentType.PAYMENT);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            log.info("PaymentService: оплата заказа {} уже выполнялась — возвращаем сохранённый результат {} без повторного списания",
                    accountRequest.getOrderUuid(), payment.getStatus());
            return new PaymentResponse(payment.getPaymentId(), payment.getStatus());
        }

        try {
            return paymentTxService.pay(accountRequest);
        } catch (DataIntegrityViolationException e) {
            Payment winner = paymentRepository.findByOrderUuidAndType(accountRequest.getOrderUuid(), PaymentType.PAYMENT)
                    .orElseThrow(() -> e);
            log.info("PaymentService: конкурентная оплата заказа {} победила — возвращаем её результат {}",
                    accountRequest.getOrderUuid(), winner.getStatus());
            return new PaymentResponse(winner.getPaymentId(), winner.getStatus());
        }
    }

    /** Идемпотентный возврат — схема та же, что и у paymentOrder (сохранённый результат/победитель гонки). */
    public PaymentResponse refundedOrder(AccountRequest accountRequest) {
        log.info("PaymentService: возврат {} на карту {} по заказу {}",
                accountRequest.getTotalAmount(),
                PaymentTxService.maskCard(accountRequest.getCardNumber()),
                accountRequest.getOrderUuid());

        Optional<Payment> existing = paymentRepository.findByOrderUuidAndType(accountRequest.getOrderUuid(), PaymentType.REFUND);
        if (existing.isPresent()) {
            Payment refund = existing.get();
            log.info("PaymentService: возврат по заказу {} уже выполнялся — возвращаем сохранённый результат без повторного зачисления",
                    accountRequest.getOrderUuid());
            return new PaymentResponse(refund.getPaymentId(), refund.getStatus());
        }

        try {
            return paymentTxService.refund(accountRequest);
        } catch (DataIntegrityViolationException e) {
            Payment winner = paymentRepository.findByOrderUuidAndType(accountRequest.getOrderUuid(), PaymentType.REFUND)
                    .orElseThrow(() -> e);
            log.info("PaymentService: конкурентный возврат заказа {} победил — возвращаем его результат",
                    accountRequest.getOrderUuid());
            return new PaymentResponse(winner.getPaymentId(), winner.getStatus());
        }
    }
}
