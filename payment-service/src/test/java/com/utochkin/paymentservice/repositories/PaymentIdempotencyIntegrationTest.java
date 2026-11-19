package com.utochkin.paymentservice.repositories;

import com.utochkin.paymentservice.PaymentServiceApplication;
import com.utochkin.paymentservice.exceptions.RefundNotPossibleException;
import com.utochkin.paymentservice.models.Account;
import com.utochkin.paymentservice.models.PaymentType;
import com.utochkin.paymentservice.models.Payment;
import com.utochkin.paymentservice.dto.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.web.AccountRequest;
import com.utochkin.paymentservice.services.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.math.BigDecimal;

@SpringBootTest(
        classes = PaymentServiceApplication.class,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.compatibility-verifier.enabled=false"
        }
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:idempotencydb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
public class PaymentIdempotencyIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final String card = "1234 5678 9012 3456";

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        paymentRepository.deleteAll();
        accountRepository.save(Account.builder().cardNumber(card).amountMoney(BigDecimal.valueOf(1000.0)).build());
    }

    private BigDecimal balance() {
        return accountRepository.findAccountByCardNumber(card).orElseThrow().getAmountMoney();
    }

    @Test
    @DisplayName("повторная оплата того же заказа не списывает деньги дважды и возвращает исходный paymentId")
    void pay_sameOrderTwice_chargesOnce() {
        UUID orderUuid = UUID.randomUUID();

        PaymentResponse first = paymentService.paymentOrder(new AccountRequest(orderUuid, BigDecimal.valueOf(100.0), card));
        PaymentResponse second = paymentService.paymentOrder(new AccountRequest(orderUuid, BigDecimal.valueOf(100.0), card));

        assertThat(first.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(second.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId());
        assertThat(balance()).isEqualByComparingTo(BigDecimal.valueOf(900.0));
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("конкурентная оплата одного заказа списывает деньги ровно один раз")
    void pay_concurrent_chargesOnce() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        AccountRequest request = new AccountRequest(orderUuid, BigDecimal.valueOf(100.0), card);

        CountDownLatch start = new CountDownLatch(1);
        Callable<PaymentResponse> task = () -> {
            start.await();
            return paymentService.paymentOrder(request);
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PaymentResponse> f1 = executor.submit(task);
            Future<PaymentResponse> f2 = executor.submit(task);
            start.countDown();

            PaymentResponse r1 = f1.get(30, TimeUnit.SECONDS);
            PaymentResponse r2 = f2.get(30, TimeUnit.SECONDS);

            assertThat(r1.getStatus()).isEqualTo(Status.SUCCESS);
            assertThat(r2.getStatus()).isEqualTo(Status.SUCCESS);
            assertThat(r1.getPaymentId()).isEqualTo(r2.getPaymentId());
            assertThat(balance()).isEqualByComparingTo(BigDecimal.valueOf(900.0));
            assertThat(paymentRepository.findAll()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("повторный возврат того же заказа не зачисляет деньги дважды")
    void refund_sameOrderTwice_depositsOnce() {
        UUID orderUuid = UUID.randomUUID();

        PaymentResponse pay = paymentService.paymentOrder(new AccountRequest(orderUuid, BigDecimal.valueOf(100.0), card));
        assertThat(pay.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(balance()).isEqualByComparingTo(BigDecimal.valueOf(900.0));

        AccountRequest refundRequest = new AccountRequest(orderUuid, BigDecimal.valueOf(100.0), card);
        PaymentResponse first = paymentService.refundedOrder(refundRequest);
        PaymentResponse second = paymentService.refundedOrder(refundRequest);

        assertThat(first.getStatus()).isEqualTo(Status.REFUNDED);
        assertThat(second.getStatus()).isEqualTo(Status.REFUNDED);
        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId()).isEqualTo(pay.getPaymentId());
        assertThat(balance()).isEqualByComparingTo(BigDecimal.valueOf(1000.0));

        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).hasSize(2);
        assertThat(payments).extracting(Payment::getType)
                .containsExactlyInAnyOrder(PaymentType.PAYMENT, PaymentType.REFUND);
    }

    @Test
    @DisplayName("возврат на другую карту отклоняется")
    void refund_anotherCard_rejected() {
        UUID orderUuid = UUID.randomUUID();
        paymentService.paymentOrder(new AccountRequest(orderUuid, BigDecimal.valueOf(100.0), card));

        assertThatThrownBy(() -> paymentService.refundedOrder(new AccountRequest(orderUuid, BigDecimal.valueOf(100.0), "9999 8888 7777 6666")))
                .isInstanceOf(RefundNotPossibleException.class);
        assertThat(balance()).isEqualByComparingTo(BigDecimal.valueOf(900.0));
    }

    @Test
    @DisplayName("неудачная оплата не блокирует повторную попытку (другой запрос, тот же заказ)")
    void pay_insufficient_thenRetryPossible() {
        UUID orderUuid = UUID.randomUUID();

        PaymentResponse failed = paymentService.paymentOrder(new AccountRequest(orderUuid, BigDecimal.valueOf(1500.0), card));
        assertThat(failed.getStatus()).isEqualTo(Status.FAILED);
        assertThat(balance()).isEqualByComparingTo(BigDecimal.valueOf(1000.0));

        transactionTemplate.executeWithoutResult(status -> accountRepository.deposit(card, BigDecimal.valueOf(1000.0)));

        PaymentResponse retried = paymentService.paymentOrder(new AccountRequest(orderUuid, BigDecimal.valueOf(1500.0), card));
        assertThat(retried.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(balance()).isEqualByComparingTo(BigDecimal.valueOf(500.0));
    }
}
