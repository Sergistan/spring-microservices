package com.utochkin.paymentservice.services;

import com.utochkin.paymentservice.exceptions.CardNumberNotFoundException;
import com.utochkin.paymentservice.exceptions.RefundNotPossibleException;
import com.utochkin.paymentservice.models.Payment;
import com.utochkin.paymentservice.models.PaymentType;
import com.utochkin.paymentservice.dto.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.repositories.AccountRepository;
import com.utochkin.paymentservice.repositories.PaymentRepository;
import com.utochkin.paymentservice.web.AccountRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Платёжные операции В ОДНОЙ транзакции БД — «сердце» идемпотентности payment-service:
 *
 *  - pay: атомарный withdraw (UPDATE баланса; 0 строк = недостаточно средств -> FAILED без записи),
 *    затем insert Payment с УНИКАЛЬНЫМ (orderUuid + type). Если конкурентный платёж уже зафиксирован,
 *    insert падает по unique-констрейнту -> ВСЯ транзакция откатывается, включая списание денег
 *    (двойная оплата одного заказа невозможна, деньги не теряются);
 *  - refund: аналогично — возврат на баланс + insert REFUND-платежа, уникальность защищает от двойного возврата.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class PaymentTxService {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Оплата в одной транзакции: withdraw (0 строк = недостаток средств -> FAILED)
     * -> insert Payment (unique orderUuid+type; конкурент -> откат всего, включая списание).
     */
    @Transactional
    public PaymentResponse pay(AccountRequest accountRequest) {
        String maskedCard = maskCard(accountRequest.getCardNumber());

        accountRepository.findAccountByCardNumber(accountRequest.getCardNumber())
                .orElseThrow(() -> {
                    log.error("PaymentTxService: не найден аккаунт с номером карты {}", maskedCard);
                    return new CardNumberNotFoundException();
                });

        BigDecimal amount = accountRequest.getTotalAmount().setScale(2, RoundingMode.HALF_UP);

        int withdrawn = accountRepository.withdraw(accountRequest.getCardNumber(), amount);
        if (withdrawn == 0) {
            log.info("PaymentTxService: недостаточно средств для списания {} с карты {}", amount, maskedCard);
            return new PaymentResponse(null, Status.FAILED);
        }

        Payment payment = Payment.builder()
                .orderUuid(accountRequest.getOrderUuid())
                .type(PaymentType.PAYMENT)
                .cardNumber(accountRequest.getCardNumber())
                .amount(amount)
                .paymentId(UUID.randomUUID())
                .status(Status.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            log.warn("PaymentTxService: конкурентный платёж по заказу {} уже зафиксирован, откатываем повторное списание",
                    accountRequest.getOrderUuid());
            throw e;
        }

        log.info("PaymentTxService: списание {} с карты {} успешно, paymentId={}", amount, maskedCard, payment.getPaymentId());
        return new PaymentResponse(payment.getPaymentId(), Status.SUCCESS);
    }

    /** Возврат в одной транзакции: депозит на карту + insert REFUND-платежа (аналогичные гарантии). */
    @Transactional
    public PaymentResponse refund(AccountRequest accountRequest) {
        Payment originalPayment = paymentRepository.findByOrderUuidAndType(accountRequest.getOrderUuid(), PaymentType.PAYMENT)
                .orElseThrow(() -> {
                    log.error("PaymentTxService: не найден платёж по заказу {} — возврат невозможен",
                            accountRequest.getOrderUuid());
                    return new RefundNotPossibleException("Ошибка: оплаченный заказ не найден, возврат невозможен!");
                });

        if (originalPayment.getStatus() != Status.SUCCESS) {
            throw new RefundNotPossibleException("Ошибка: заказ не был успешно оплачен, возврат невозможен!");
        }

        if (!originalPayment.getCardNumber().equals(accountRequest.getCardNumber())) {
            log.warn("PaymentTxService: попытка возврата по заказу {} на другую карту", accountRequest.getOrderUuid());
            throw new RefundNotPossibleException("Ошибка: возврат возможен только на карту, с которой была оплата!");
        }

        accountRepository.findAccountByCardNumber(accountRequest.getCardNumber())
                .orElseThrow(CardNumberNotFoundException::new);

        BigDecimal refundAmount = originalPayment.getAmount();
        accountRepository.deposit(accountRequest.getCardNumber(), refundAmount);

        Payment refund = Payment.builder()
                .orderUuid(accountRequest.getOrderUuid())
                .type(PaymentType.REFUND)
                .cardNumber(accountRequest.getCardNumber())
                .amount(refundAmount)
                .paymentId(originalPayment.getPaymentId())
                .status(Status.REFUNDED)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            paymentRepository.saveAndFlush(refund);
        } catch (DataIntegrityViolationException e) {
            log.warn("PaymentTxService: конкурентный возврат по заказу {} уже зафиксирован, откатываем повторное зачисление",
                    accountRequest.getOrderUuid());
            throw e;
        }

        log.info("PaymentTxService: возврат {} на карту {} успешно", refundAmount, maskCard(accountRequest.getCardNumber()));
        return new PaymentResponse(originalPayment.getPaymentId(), Status.REFUNDED);
    }

    /** Маскировка карты для логов: видны только последние 4 цифры. */
    static String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
