package com.utochkin.paymentservice.services;

import com.utochkin.paymentservice.exceptions.RefundNotPossibleException;
import com.utochkin.paymentservice.models.PaymentType;
import com.utochkin.paymentservice.models.Payment;
import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.repositories.PaymentRepository;
import com.utochkin.paymentservice.requests.AccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
public class PaymentServiceTest {

    @Mock
    private PaymentTxService paymentTxService;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    private UUID orderUuid;
    private AccountRequest request;

    @BeforeEach
    void setUp() {
        orderUuid = UUID.randomUUID();
        request = new AccountRequest(orderUuid, BigDecimal.valueOf(100.0), "1234 5678 9012 3456");
    }

    private Payment payment(PaymentType type, Status status, UUID paymentId) {
        return Payment.builder()
                .orderUuid(orderUuid)
                .type(type)
                .cardNumber(request.getCardNumber())
                .amount(BigDecimal.valueOf(100.0))
                .paymentId(paymentId)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("paymentOrder: при первом обращении делегирует в PaymentTxService")
    void paymentOrder_firstCall_delegates() {
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT))
                .thenReturn(Optional.empty());
        PaymentResponse success = new PaymentResponse(UUID.randomUUID(), Status.SUCCESS);
        when(paymentTxService.pay(request)).thenReturn(success);

        PaymentResponse response = paymentService.paymentOrder(request);

        assertThat(response).isEqualTo(success);
        verify(paymentTxService).pay(request);
    }

    @Test
    @DisplayName("paymentOrder: повторный запрос возвращает сохранённый результат без списания")
    void paymentOrder_duplicate_returnsStoredResult() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT))
                .thenReturn(Optional.of(payment(PaymentType.PAYMENT, Status.SUCCESS, paymentId)));

        PaymentResponse response = paymentService.paymentOrder(request);

        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        verify(paymentTxService, never()).pay(any());
    }

    @Test
    @DisplayName("paymentOrder: при конкурентном дубликате возвращает результат победителя")
    void paymentOrder_concurrentDuplicate_returnsWinnerResult() {
        UUID winnerPaymentId = UUID.randomUUID();
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(payment(PaymentType.PAYMENT, Status.SUCCESS, winnerPaymentId)));
        when(paymentTxService.pay(request)).thenThrow(new DataIntegrityViolationException("duplicate"));

        PaymentResponse response = paymentService.paymentOrder(request);

        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(response.getPaymentId()).isEqualTo(winnerPaymentId);
    }

    @Test
    @DisplayName("paymentOrder: если после конфликта запись не найдена — исключение пробрасывается")
    void paymentOrder_conflictWithoutWinner_rethrows() {
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.PAYMENT))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(paymentTxService.pay(request)).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> paymentService.paymentOrder(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("refundedOrder: при первом обращении делегирует в PaymentTxService")
    void refundedOrder_firstCall_delegates() {
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.REFUND))
                .thenReturn(Optional.empty());
        PaymentResponse refunded = new PaymentResponse(UUID.randomUUID(), Status.REFUNDED);
        when(paymentTxService.refund(request)).thenReturn(refunded);

        PaymentResponse response = paymentService.refundedOrder(request);

        assertThat(response).isEqualTo(refunded);
        verify(paymentTxService).refund(request);
    }

    @Test
    @DisplayName("refundedOrder: повторный возврат возвращает сохранённый результат без зачисления")
    void refundedOrder_duplicate_returnsStoredResult() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.REFUND))
                .thenReturn(Optional.of(payment(PaymentType.REFUND, Status.REFUNDED, paymentId)));

        PaymentResponse response = paymentService.refundedOrder(request);

        assertThat(response.getStatus()).isEqualTo(Status.REFUNDED);
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        verify(paymentTxService, never()).refund(any());
    }

    @Test
    @DisplayName("refundedOrder: бизнес-ошибки пробрасываются наверх")
    void refundedOrder_refundNotPossible_rethrows() {
        when(paymentRepository.findByOrderUuidAndType(orderUuid, PaymentType.REFUND))
                .thenReturn(Optional.empty());
        when(paymentTxService.refund(request)).thenThrow(new RefundNotPossibleException());

        assertThatThrownBy(() -> paymentService.refundedOrder(request))
                .isInstanceOf(RefundNotPossibleException.class);
    }
}
