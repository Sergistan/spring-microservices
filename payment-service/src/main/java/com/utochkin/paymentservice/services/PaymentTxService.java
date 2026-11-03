package com.utochkin.paymentservice.services;

import com.utochkin.paymentservice.exceptions.CardNumberNotFoundException;
import com.utochkin.paymentservice.exceptions.RefundNotPossibleException;
import com.utochkin.paymentservice.models.Payment;
import com.utochkin.paymentservice.models.PaymentType;
import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.repositories.AccountRepository;
import com.utochkin.paymentservice.repositories.PaymentRepository;
import com.utochkin.paymentservice.requests.AccountRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class PaymentTxService {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentResponse pay(AccountRequest accountRequest) {
        String maskedCard = maskCard(accountRequest.getCardNumber());

        accountRepository.findAccountByCardNumber(accountRequest.getCardNumber())
                .orElseThrow(() -> {
                    log.error("PaymentTxService: не найден аккаунт с номером карты {}", maskedCard);
                    return new CardNumberNotFoundException();
                });

        int withdrawn = accountRepository.withdraw(accountRequest.getCardNumber(), accountRequest.getTotalAmount());
        if (withdrawn == 0) {
            log.info("PaymentTxService: недостаточно средств для списания {} с карты {}",
                    accountRequest.getTotalAmount(), maskedCard);
            return new PaymentResponse(null, Status.FAILED);
        }

        Payment payment = Payment.builder()
                .orderUuid(accountRequest.getOrderUuid())
                .type(PaymentType.PAYMENT)
                .cardNumber(accountRequest.getCardNumber())
                .amount(accountRequest.getTotalAmount())
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

        log.info("PaymentTxService: списание {} с карты {} успешно, paymentId={}",
                accountRequest.getTotalAmount(), maskedCard, payment.getPaymentId());
        return new PaymentResponse(payment.getPaymentId(), Status.SUCCESS);
    }

    @Transactional
    public PaymentResponse refund(AccountRequest accountRequest) {
        Payment originalPayment = paymentRepository.findByOrderUuidAndType(accountRequest.getOrderUuid(), PaymentType.PAYMENT)
                .orElseThrow(() -> {
                    log.error("PaymentTxService: не найден платёж по заказу {} — возврат невозможен",
                            accountRequest.getOrderUuid());
                    return new RefundNotPossibleException("Error: paid order not found, refund not possible!");
                });

        if (originalPayment.getStatus() != Status.SUCCESS) {
            throw new RefundNotPossibleException("Error: order was not paid successfully, refund not possible!");
        }

        if (!originalPayment.getCardNumber().equals(accountRequest.getCardNumber())) {
            log.warn("PaymentTxService: попытка возврата по заказу {} на другую карту", accountRequest.getOrderUuid());
            throw new RefundNotPossibleException("Error: refund is only possible to the card used for payment!");
        }

        accountRepository.findAccountByCardNumber(accountRequest.getCardNumber())
                .orElseThrow(CardNumberNotFoundException::new);

        accountRepository.deposit(accountRequest.getCardNumber(), originalPayment.getAmount());

        Payment refund = Payment.builder()
                .orderUuid(accountRequest.getOrderUuid())
                .type(PaymentType.REFUND)
                .cardNumber(accountRequest.getCardNumber())
                .amount(originalPayment.getAmount())
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

        log.info("PaymentTxService: возврат {} на карту {} успешно",
                originalPayment.getAmount(), maskCard(accountRequest.getCardNumber()));
        return new PaymentResponse(originalPayment.getPaymentId(), Status.REFUNDED);
    }

    static String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }
}
