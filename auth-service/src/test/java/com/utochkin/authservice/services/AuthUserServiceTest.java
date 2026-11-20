package com.utochkin.authservice.services;

import com.utochkin.authservice.exceptions.ActiveOrdersExistException;
import com.utochkin.authservice.exceptions.ServiceUnavailableException;
import com.utochkin.authservice.exceptions.UserAlreadyExistsException;
import com.utochkin.authservice.exceptions.UserNotFoundException;
import com.utochkin.authservice.keycloak.KeycloakAdminClient;
import com.utochkin.authservice.keycloak.KeycloakRole;
import com.utochkin.authservice.web.RegisterRequest;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class AuthUserServiceTest {

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private ActiveOrdersChecker activeOrdersChecker;

    @InjectMocks
    private AuthUserService authUserService;

    private final RegisterRequest request = new RegisterRequest(
            "ivanov", "ivanov@mail.ru", "Иван", "Иванов", "Str0ngPass!", "COURIER");

    @Test
    @DisplayName("register: создаёт пользователя и назначает client-role")
    void register_success() {
        given(keycloakAdminClient.createUser("ivanov", "ivanov@mail.ru", "Иван", "Иванов", "Str0ngPass!"))
                .willReturn("user-id-1");
        given(keycloakAdminClient.getClientUuid()).willReturn("client-uuid");
        KeycloakRole courierRole = new KeycloakRole("role-id", "COURIER");
        given(keycloakAdminClient.getClientRole("client-uuid", "COURIER")).willReturn(courierRole);

        authUserService.register(request);

        then(keycloakAdminClient).should().assignClientRole("user-id-1", "client-uuid", courierRole);
    }

    @Test
    @DisplayName("register: 409 от Keycloak (username/email занят) → UserAlreadyExistsException, роль не назначается")
    void register_conflict() {
        given(keycloakAdminClient.createUser(any(), any(), any(), any(), any()))
                .willThrow(HttpClientErrorException.create(
                        HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertThrows(UserAlreadyExistsException.class, () -> authUserService.register(request));

        then(keycloakAdminClient).should(never()).assignClientRole(any(), any(), any());
    }

    @Test
    @DisplayName("fallbackRegisterUser: бизнес-отказ пробрасывается, технический сбой → 503")
    void fallbackRegisterUser_mapping() {
        assertThrows(UserAlreadyExistsException.class,
                () -> authUserService.fallbackRegisterUser(request, new UserAlreadyExistsException()));

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class,
                () -> authUserService.fallbackRegisterUser(request, new RuntimeException("timeout")));
        assertThat(ex.getMessage()).contains("Сервис временно недоступен");
    }

    @Test
    @DisplayName("deleteOwnAccount: есть активные заказы → ActiveOrdersExistException, удаление не выполняется")
    void deleteOwnAccount_activeOrders() {
        given(activeOrdersChecker.hasActiveOrders("sub-42")).willReturn(true);

        assertThrows(ActiveOrdersExistException.class, () -> authUserService.deleteOwnAccount("sub-42"));

        then(keycloakAdminClient).should(never()).deleteUser(any());
    }

    @Test
    @DisplayName("deleteOwnAccount: заказов нет → пользователь удаляется из Keycloak")
    void deleteOwnAccount_clean() {
        given(activeOrdersChecker.hasActiveOrders("sub-42")).willReturn(false);
        given(keycloakAdminClient.deleteUser("sub-42")).willReturn(true);

        authUserService.deleteOwnAccount("sub-42");

        then(keycloakAdminClient).should().deleteUser("sub-42");
    }

    @Test
    @DisplayName("deleteOwnAccount: Keycloak вернул 404 → UserNotFoundException")
    void deleteOwnAccount_userNotFound() {
        given(activeOrdersChecker.hasActiveOrders("sub-42")).willReturn(false);
        given(keycloakAdminClient.deleteUser("sub-42")).willReturn(false);

        assertThrows(UserNotFoundException.class, () -> authUserService.deleteOwnAccount("sub-42"));
    }

    @Test
    @DisplayName("deleteOwnAccount: order-service недоступен (FeignException после retry) → 503, без удаления")
    void deleteOwnAccount_orderServiceUnavailable() {
        Request httpRequest = Request.create(Request.HttpMethod.GET, "http://order/x",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response httpResponse = Response.builder()
                .status(500).reason("Internal Server Error").request(httpRequest).build();
        given(activeOrdersChecker.hasActiveOrders("sub-42"))
                .willThrow(FeignException.errorStatus("OrderServiceClient#hasActiveOrders", httpResponse));

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class,
                () -> authUserService.deleteOwnAccount("sub-42"));
        assertThat(ex.getMessage()).contains("Не удалось проверить активные заказы");

        then(keycloakAdminClient).should(never()).deleteUser(any());
    }
}
