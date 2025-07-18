package com.utochkin.orderservice.controllers;

import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.request.AccountRequest;
import com.utochkin.orderservice.request.PaymentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentController paymentController;

    @Test
    @DisplayName("paymentOrder возвращает SUCCESS‑ответ и вызывается с правильным телом")
    void paymentOrder_Success() {
        UUID expectedId = UUID.randomUUID();
        PaymentResponse fakeResp = new PaymentResponse(expectedId, Status.SUCCESS);

        given(paymentController.paymentOrder(new AccountRequest(123.0, "1111 2222 3333 4444")))
                .willReturn(fakeResp);

        PaymentResponse actual = paymentController.paymentOrder(
                new AccountRequest(123.0, "1111 2222 3333 4444")
        );

        assertThat(actual.getPaymentId()).isEqualTo(expectedId);
        assertThat(actual.getStatus()).isEqualTo(Status.SUCCESS);

        ArgumentCaptor<AccountRequest> captor = ArgumentCaptor.forClass(AccountRequest.class);
        verify(paymentController).paymentOrder(captor.capture());
        assertThat(captor.getValue().getTotalAmount()).isEqualTo(123.0);
        assertThat(captor.getValue().getCardNumber()).isEqualTo("1111 2222 3333 4444");
    }

    @Test
    @DisplayName("refundedOrder возвращает REFUNDED‑ответ")
    void refundedOrder_Success() {
        UUID expectedId = UUID.randomUUID();
        PaymentResponse fakeResp = new PaymentResponse(expectedId, Status.REFUNDED);
        given(paymentController.refundedOrder(new AccountRequest(456.0, "9999 8888 7777 6666")))
                .willReturn(fakeResp);

        PaymentResponse actual = paymentController.refundedOrder(
                new AccountRequest(456.0, "9999 8888 7777 6666")
        );

        assertThat(actual.getPaymentId()).isEqualTo(expectedId);
        assertThat(actual.getStatus()).isEqualTo(Status.REFUNDED);

        verify(paymentController).refundedOrder(new AccountRequest(456.0, "9999 8888 7777 6666"));
    }
}