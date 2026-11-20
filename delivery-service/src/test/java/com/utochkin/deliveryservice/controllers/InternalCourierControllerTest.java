package com.utochkin.deliveryservice.controllers;

import com.utochkin.deliveryservice.config.Config;
import com.utochkin.deliveryservice.services.DeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InternalCourierController.class)
@Import({Config.class, com.utochkin.deliveryservice.exceptions.ExceptionControllerAdvice.class})
@ActiveProfiles("test")
public class InternalCourierControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DeliveryService deliveryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID subId = UUID.randomUUID();

    @Test
    @DisplayName("GET active-deliveries-count — 200, сервисный токен auth-service-client")
    void count_withAuthServiceToken_returns200() throws Exception {
        given(deliveryService.activeDeliveriesCount(subId)).willReturn(2L);

        mvc.perform(get("/delivery/api/v1/couriers/{subId}/active-deliveries-count", subId)
                        .with(jwt().jwt(j -> j.claim("azp", "auth-service-client"))))
                .andExpect(status().isOk())
                .andExpect(content().string("2"));
    }

    @Test
    @DisplayName("GET active-deliveries-count — 403, чужой azp (обычный пользователь/другой сервис)")
    void count_withWrongClient_returns403() throws Exception {
        mvc.perform(get("/delivery/api/v1/couriers/{subId}/active-deliveries-count", subId)
                        .with(jwt().jwt(j -> j.claim("azp", "spring-microservices"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageError").value("Ошибка: доступ разрешён только внутренним сервисам"));
    }

    @Test
    @DisplayName("GET active-deliveries-count — 401 без JWT")
    void count_withoutJwt_returns401() throws Exception {
        mvc.perform(get("/delivery/api/v1/couriers/{subId}/active-deliveries-count", subId))
                .andExpect(status().isUnauthorized());
    }
}
