package com.utochkin.paymentservice.services;

import com.utochkin.paymentservice.exceptions.CardNumberNotFoundException;
import com.utochkin.paymentservice.exceptions.RefundNotPossibleException;
import com.utochkin.paymentservice.models.PaymentType;
import com.utochkin.paymentservice.models.Account;
import com.utochkin.paymentservice.models.Payment;
import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.repositories.AccountRepository;
import com.utochkin.paymentservice.repositories.PaymentRepository;
import com.utochkin.paymentservice.requests.AccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
public class PaymentTxServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentTxService paymentTxService;

    private UUID orderUuid;
    private AccountRequest request;

    @BeforeEach
    void setUp() {
        orderUuid = UUID.randomUUID();
        request = new AccountRequest(orderUuid, BigDecimal.valueOf(100.0), "1234 5678 9012 3456");
    }

    private Account account() {
        return Account.builder()
                .id(1L)
                .cardNumber(request.getCardNumber())
                .amountMoney(BigDecimal.valueOf(1000.0))
                .build();
    }

    private Payment originalPayment(Status status) {
        return Payment.builder()
                .orderUuid(orderUuid)
                .type(PaymentType.PAYMENT)
                .cardNumber(request.getCardNumber())
                .amount(BigDecimal.valueOf(100.0))
                .paymentId(UUID.randomUUID())
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("pay: успешное списание — атомарный withdraw + запись платежа SUCCESS")
    void pay_successful() {
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.of(account()));
        when(accountRepository.withdraw(request.getCardNumber(), BigDecimal.valueOf(100.0).setScale(2))).thenReturn(1);
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentTxService.pay(request);

        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(response.getPaymentId()).isNotNull();

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getOrderUuid()).isEqualTo(orderUuid);
        assertThat(saved.getType()).isEqualTo(PaymentType.PAYMENT);
        assertThat(saved.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(saved.getCardNumber()).isEqualTo(request.getCardNumber());
    }

    @Test
    @DisplayName("pay: недостаточно средств — withdraw вернул 0 строк, ответ FAILED, платеж не записывается")
    void pay_insufficientFunds_failed() {
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.of(account()));
        when(accountRepository.withdraw(request.getCardNumber(), BigDecimal.valueOf(100.0).setScale(2))).thenReturn(0);

        PaymentResponse response = paymentTxService.pay(request);

        assertThat(response.getStatus()).isEqualTo(Status.FAILED);
        assertThat(response.getPaymentId()).isNull();
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("pay: карта не найдена — CardNumberNotFoundException, списания нет")
    void pay_cardNotFound_throws() {
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentTxService.pay(request))
                .isInstanceOf(CardNumberNotFoundException.class)
                .hasMessageContaining("Ошибка: карта с таким номером не найдена");
        verify(accountRepository, never()).withdraw(any(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("pay: конфликт уникальности (конкурентный дубликат) — исключение пробрасывается для отката tx")
    void pay_uniqueConflict_rethrows() {
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.of(account()));
        when(accountRepository.withdraw(request.getCardNumber(), BigDecimal.valueOf(100.0).setScale(2))).thenReturn(1);
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> paymentTxService.pay(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("refund: успешный возврат — deposit на сумму ИСХОДНОГО платежа, paymentId оригинала")
    void refund_successful() {
        Payment original = originalPayment(Status.SUCCESS);
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT))
                .thenReturn(Optional.of(original));
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.of(account()));
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentTxService.refund(request);

        assertThat(response.getStatus()).isEqualTo(Status.REFUNDED);
        assertThat(response.getPaymentId()).isEqualTo(original.getPaymentId());
        verify(accountRepository).deposit(request.getCardNumber(), original.getAmount());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(PaymentType.REFUND);
        assertThat(captor.getValue().getPaymentId()).isEqualTo(original.getPaymentId());
    }

    @Test
    @DisplayName("refund: исходный платеж не найден — RefundNotPossibleException")
    void refund_noOriginalPayment_throws() {
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentTxService.refund(request))
                .isInstanceOf(RefundNotPossibleException.class);
        verify(accountRepository, never()).deposit(any(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("refund: заказ не был успешно оплачен — RefundNotPossibleException")
    void refund_originalNotSuccess_throws() {
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT))
                .thenReturn(Optional.of(originalPayment(Status.FAILED)));

        assertThatThrownBy(() -> paymentTxService.refund(request))
                .isInstanceOf(RefundNotPossibleException.class);
        verify(accountRepository, never()).deposit(any(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("refund: возврат на другую карту — RefundNotPossibleException")
    void refund_anotherCard_throws() {
        Payment original = originalPayment(Status.SUCCESS);
        request.setCardNumber("9999 8888 7777 6666");
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT))
                .thenReturn(Optional.of(original));

        assertThatThrownBy(() -> paymentTxService.refund(request))
                .isInstanceOf(RefundNotPossibleException.class)
                .hasMessageContaining("возврат возможен только на карту");
        verify(accountRepository, never()).deposit(any(), any(BigDecimal.class));
    }

    @Test
    @DisplayName("refund: конфликт уникальности (двойной возврат) — исключение пробрасывается")
    void refund_uniqueConflict_rethrows() {
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT))
                .thenReturn(Optional.of(originalPayment(Status.SUCCESS)));
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.of(account()));
        when(paymentRepository.saveAndFlush(any(Payment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> paymentTxService.refund(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("maskCard: маскирует все цифры кроме последних 4")
    void maskCard_masksAllButLast4() {
        assertThat(PaymentTxService.maskCard("1234 5678 9012 3456")).isEqualTo("**** **** **** 3456");
        assertThat(PaymentTxService.maskCard(null)).isEqualTo("****");
    }
}
