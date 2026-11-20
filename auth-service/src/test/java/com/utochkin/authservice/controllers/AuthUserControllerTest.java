package com.utochkin.authservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.authservice.config.Config;
import com.utochkin.authservice.exceptions.ActiveOrdersExistException;
import com.utochkin.authservice.exceptions.UserAlreadyExistsException;
import com.utochkin.authservice.services.AuthUserService;
import com.utochkin.authservice.web.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthUserController.class)
@Import(Config.class)
@ActiveProfiles("test")
public class AuthUserControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private AuthUserService authUserService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final RegisterRequest validRequest = new RegisterRequest(
            "ivanov", "ivanov@mail.ru", "Иван", "Иванов", "Str0ngPass!", "USER");

    @Test
    @DisplayName("POST /auth/api/v1/users — 201, анонимная регистрация без JWT")
    void register_Anonymous_Returns201() throws Exception {
        mvc.perform(post("/auth/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /auth/api/v1/users — 400, некорректные поля (username/пароль/роль)")
    void register_InvalidBody_Returns400() throws Exception {
        RegisterRequest bad = new RegisterRequest("И", "not-email", "", "Ivanov", "123", "ADMIN");

        mvc.perform(post("/auth/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageError").exists());
    }

    @Test
    @DisplayName("POST /auth/api/v1/users — 409, username/email уже занят")
    void register_Conflict_Returns409() throws Exception {
        willThrow(new UserAlreadyExistsException()).given(authUserService).register(any(RegisterRequest.class));

        mvc.perform(post("/auth/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messageError").value("Пользователь с таким username или email уже существует"));
    }

    @Test
    @DisplayName("DELETE /auth/api/v1/users/me — 204, удаление с JWT")
    void deleteOwnAccount_WithJwt_Returns204() throws Exception {
        mvc.perform(delete("/auth/api/v1/users/me")
                        .with(jwt())
                        .header("X-User-SubId", "sub-42"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /auth/api/v1/users/me — 401 без JWT")
    void deleteOwnAccount_WithoutJwt_Returns401() throws Exception {
        mvc.perform(delete("/auth/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /auth/api/v1/users/me — 400, есть активные заказы")
    void deleteOwnAccount_ActiveOrders_Returns400() throws Exception {
        willThrow(new ActiveOrdersExistException()).given(authUserService).deleteOwnAccount("sub-42");

        mvc.perform(delete("/auth/api/v1/users/me")
                        .with(jwt())
                        .header("X-User-SubId", "sub-42"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageError").value("Нельзя удалить аккаунт: есть незавершённые заказы или доставки"));
    }
}
