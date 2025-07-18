package com.utochkin.paymentservice.services;

import com.utochkin.paymentservice.exceptions.CardNumberNotFoundException;
import com.utochkin.paymentservice.models.Account;
import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.repositories.AccountRepository;
import com.utochkin.paymentservice.requests.AccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Account account;
    private AccountRequest request;

    @BeforeEach
    void setUp() {
        account = Account.builder()
                .id(1L)
                .cardNumber("1234 5678 9012 3456")
                .amountMoney(100.0)
                .build();
        request = new AccountRequest(50.0, account.getCardNumber());
    }

    @Test
    @DisplayName("paymentOrder: успешный платеж при достаточном балансе")
    void paymentOrder_successful() {
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.of(account));

        PaymentResponse response = paymentService.paymentOrder(request);

        assertThat(response.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(response.getPaymentId()).isNotNull();
        assertThat(account.getAmountMoney()).isEqualTo(50.0);
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("paymentOrder: возврат FAILED при недостаточном балансе")
    void paymentOrder_insufficient() {
        account.setAmountMoney(30.0);
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.of(account));

        PaymentResponse response = paymentService.paymentOrder(request);

        assertThat(response.getStatus()).isEqualTo(Status.FAILED);
        assertThat(response.getPaymentId()).isNull();
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("paymentOrder: выдает исключение CardNumberNotFoundException при отсутствии account")
    void paymentOrder_noAccount_throws() {
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.paymentOrder(request))
                .isInstanceOf(CardNumberNotFoundException.class)
                .hasMessageContaining("Error: card number not found");
    }

    @Test
    @DisplayName("refundedOrder: успешный возврат средств обновляет баланс и возвращает REFUNDED")
    void refundedOrder_successful() {
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.of(account));

        PaymentResponse response = paymentService.refundedOrder(request);

        assertThat(response.getStatus()).isEqualTo(Status.REFUNDED);
        assertThat(response.getPaymentId()).isNull();
        assertThat(account.getAmountMoney()).isEqualTo(150.0);
        verify(accountRepository).save(account);
    }

    @Test
    @DisplayName("refundedOrder: выдает исключение CardNumberNotFoundException при отсутствии account")
    void refundedOrder_noAccount_throws() {
        when(accountRepository.findAccountByCardNumber(request.getCardNumber()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refundedOrder(request))
                .isInstanceOf(CardNumberNotFoundException.class)
                .hasMessageContaining("Error: card number not found");
    }
}

