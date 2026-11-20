package com.utochkin.authservice.services;

import com.utochkin.authservice.exceptions.ActiveOrdersExistException;
import com.utochkin.authservice.exceptions.ServiceUnavailableException;
import com.utochkin.authservice.exceptions.UserAlreadyExistsException;
import com.utochkin.authservice.exceptions.UserNotFoundException;
import com.utochkin.authservice.keycloak.KeycloakAdminClient;
import com.utochkin.authservice.keycloak.KeycloakRole;
import com.utochkin.authservice.web.RegisterRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Бизнес-логика регистрации и удаления аккаунта.
 *
 * register: создать пользователя в Keycloak (Admin REST) и назначить ему client-role
 * (USER или COURIER — whitelist в RegisterRequest). Роль обязательна для AuthServerRoleConverter
 * сервисов: без неё пользователь пройдёт аутентификацию, но не получит никаких прав.
 *
 * deleteOwnAccount: спросить order-service о незавершённых заказах (Feign, сервисный токен,
 * retry в ActiveOrdersChecker) и только при «чистом» аккаунте удалить пользователя из Keycloak.
 * Локальные зеркала users в order-service и история заказов в Mongo НЕ удаляются —
 * завершённые заказы должны сохраниться.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AuthUserService {

    private final KeycloakAdminClient keycloakAdminClient;
    private final ActiveOrdersChecker activeOrdersChecker;

    /**
     * Саморегистрация: POST /users + назначение client-role. 409 от Keycloak мапится в
     * UserAlreadyExistsException. @Retry НЕ ставится сознательно: POST не идемпотентен —
     * повтор после таймаута создал бы пользователя повторно (409), а первый успех потерялся.
     * CircuitBreaker защищает от шторма запросов при лежащем Keycloak (fallback — 503).
     */
    @CircuitBreaker(name = "circuitBreakerRegisterUser", fallbackMethod = "fallbackRegisterUser")
    public void register(RegisterRequest request) {
        log.info("AuthUserService: регистрация пользователя {} с ролью {}", request.username(), request.role());

        String userId;
        try {
            userId = keycloakAdminClient.createUser(
                    request.username(), request.email(), request.firstName(), request.lastName(), request.password());
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("AuthUserService: username/email {} уже занят (409 от Keycloak)", request.username());
            throw new UserAlreadyExistsException();
        }

        String clientUuid = keycloakAdminClient.getClientUuid();
        KeycloakRole role = keycloakAdminClient.getClientRole(clientUuid, request.role());
        keycloakAdminClient.assignClientRole(userId, clientUuid, role);

        log.info("AuthUserService: пользователь {} (id={}) создан, назначена роль {}", request.username(), userId, request.role());
    }

    /** Fallback register: бизнес-отказ пробрасываем как есть, технический сбой — 503. */
    public void fallbackRegisterUser(RegisterRequest request, Throwable throwable) {
        if (throwable instanceof UserAlreadyExistsException) {
            throw (UserAlreadyExistsException) throwable;
        }
        log.error("Fallback для register сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }

    /**
     * Удаление собственного аккаунта: сначала проверка активных заказов в order-service
     * (с retry — если сервис недоступен, лучше отказать в удалении, чем удалить «вслепую»),
     * затем DELETE пользователя из Keycloak.
     */
    public void deleteOwnAccount(String subId) {
        log.info("AuthUserService: запрос на удаление аккаунта subId={}", subId);

        Boolean hasActive;
        try {
            hasActive = activeOrdersChecker.hasActiveOrders(subId);
        } catch (FeignException e) {
            log.error("AuthUserService: order-service недоступен при проверке активных заказов ({})", e.getMessage());
            throw new ServiceUnavailableException("Не удалось проверить активные заказы, попробуйте позже");
        }

        if (Boolean.TRUE.equals(hasActive)) {
            log.warn("AuthUserService: у subId={} есть незавершённые заказы — удаление запрещено", subId);
            throw new ActiveOrdersExistException();
        }

        boolean deleted = keycloakAdminClient.deleteUser(subId);
        if (!deleted) {
            throw new UserNotFoundException();
        }
        log.info("AuthUserService: аккаунт subId={} удалён из Keycloak", subId);
    }
}
