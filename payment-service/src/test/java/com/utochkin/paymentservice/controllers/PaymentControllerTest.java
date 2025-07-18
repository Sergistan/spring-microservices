package com.utochkin.paymentservice.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.paymentservice.config.AuthServerRoleConverter;
import com.utochkin.paymentservice.config.Config;
import com.utochkin.paymentservice.exceptions.CardNumberNotFoundException;
import com.utochkin.paymentservice.exceptions.CustomAccessDeniedHandler;
import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.requests.AccountRequest;
import com.utochkin.paymentservice.services.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({
        Config.class,
        AuthServerRoleConverter.class,
        CustomAccessDeniedHandler.class
})
@ActiveProfiles("test")
@WithMockUser(roles = "USER")
public class PaymentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("POST /payment/api/v1/pay — 200, SUCCESS")
    void paymentOrder_Success() throws Exception {
        AccountRequest req = new AccountRequest(100.0, "1111 2222 3333 4444");
        PaymentResponse resp = new PaymentResponse(UUID.randomUUID(), Status.SUCCESS);

        given(paymentService.paymentOrder(any(AccountRequest.class))).willReturn(resp);

        mvc.perform(post("/payment/api/v1/pay")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(resp.getPaymentId().toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /payment/api/v1/pay — 404, CardNumberNotFoundException")
    void paymentOrder_CardNotFound_NotFound() throws Exception {
        AccountRequest req = new AccountRequest(50.0, "9999 8888 7777 6666");

        given(paymentService.paymentOrder(any(AccountRequest.class)))
                .willThrow(new CardNumberNotFoundException());

        mvc.perform(post("/payment/api/v1/pay")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.messageError").value("Error: card number not found!"));
    }

    @Test
    @DisplayName("POST /payment/api/v1/refunded — 200, REFUNDED")
    void refundedOrder_Success() throws Exception {
        AccountRequest req = new AccountRequest(75.0, "1111 2222 3333 4444");
        PaymentResponse resp = new PaymentResponse(UUID.randomUUID(), Status.REFUNDED);

        given(paymentService.refundedOrder(any(AccountRequest.class))).willReturn(resp);

        mvc.perform(post("/payment/api/v1/refunded")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("POST /payment/api/v1/refunded — 404, CardNumberNotFoundException")
    void refundedOrder_CardNotFound() throws Exception {
        AccountRequest req = new AccountRequest(20.0, "0000 1111 2222 3333");

        given(paymentService.refundedOrder(any(AccountRequest.class)))
                .willThrow(new CardNumberNotFoundException());

        mvc.perform(post("/payment/api/v1/refunded")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.messageError").value("Error: card number not found!"));
    }
}
