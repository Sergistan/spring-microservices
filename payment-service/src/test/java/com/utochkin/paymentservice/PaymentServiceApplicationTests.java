package com.utochkin.paymentservice;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.paymentservice.config.AuthServerRoleConverter;
import com.utochkin.paymentservice.config.Config;
import com.utochkin.paymentservice.exceptions.CustomAccessDeniedHandler;
import com.utochkin.paymentservice.exceptions.ExceptionControllerAdvice;
import com.utochkin.paymentservice.models.Account;
import com.utochkin.paymentservice.repositories.AccountRepository;
import com.utochkin.paymentservice.requests.AccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PaymentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import({
        Config.class,
        AuthServerRoleConverter.class,
        CustomAccessDeniedHandler.class,
        ExceptionControllerAdvice.class
})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.cloud.compatibility-verifier.enabled=false",
        "spring.liquibase.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
@WithMockUser(roles = "USER")
class PaymentServiceApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String EXISTING_CARD = "1111 2222 3333 4444";
    private static final String MISSING_CARD = "0000 0000 0000 0000";

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        accountRepository.save(Account.builder()
                .cardNumber(EXISTING_CARD)
                .amountMoney(500.0)
                .build()
        );
    }

    @Test
    @DisplayName("POST /payment/api/v1/pay — SUCCESS когда достаточно средств")
    void pay_success() throws Exception {
        var req = new AccountRequest(200.0, EXISTING_CARD);
        mvc.perform(post("/payment/api/v1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentId").isNotEmpty());

        var acct = accountRepository.findAccountByCardNumber(EXISTING_CARD).get();
        assertThat(acct.getAmountMoney()).isEqualTo(300.0);
    }

    @Test
    @DisplayName("POST /payment/api/v1/pay — FAILED при недостаточности средств")
    void pay_insufficient() throws Exception {
        var req = new AccountRequest(600.0, EXISTING_CARD);
        mvc.perform(post("/payment/api/v1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.paymentId").isEmpty());

        var acct = accountRepository.findAccountByCardNumber(EXISTING_CARD).get();
        assertThat(acct.getAmountMoney()).isEqualTo(500.0);
    }

    @Test
    @DisplayName("POST /payment/api/v1/pay — 404 когда карта не найдена")
    void pay_cardNotFound() throws Exception {
        var req = new AccountRequest(100.0, MISSING_CARD);
        mvc.perform(post("/payment/api/v1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.messageError").value("Error: card number not found!"));
    }

    @Test
    @DisplayName("POST /payment/api/v1/refunded — REFUNDED когда карта существует")
    void refunded_success() throws Exception {
        var req = new AccountRequest(150.0, EXISTING_CARD);
        mvc.perform(post("/payment/api/v1/refunded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));

        var acct = accountRepository.findAccountByCardNumber(EXISTING_CARD).get();
        assertThat(acct.getAmountMoney()).isEqualTo(650.0);
    }

    @Test
    @DisplayName("POST /payment/api/v1/refunded — 404 когда карта не найдена")
    void refunded_cardNotFound() throws Exception {
        var req = new AccountRequest(50.0, MISSING_CARD);
        mvc.perform(post("/payment/api/v1/refunded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.messageError").value("Error: card number not found!"));
    }
}

