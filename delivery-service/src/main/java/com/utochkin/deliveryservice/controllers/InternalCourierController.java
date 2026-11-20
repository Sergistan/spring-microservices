package com.utochkin.deliveryservice.controllers;

import com.utochkin.deliveryservice.exceptions.OrderAccessDeniedException;
import com.utochkin.deliveryservice.services.DeliveryService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Внутренние эндпоинты delivery-service (наружу через gateway не выставляются — сервис
 * внутренний; вызываются по Eureka с сервисным токеном). Доступ — только клиенту
 * auth-service-client (проверка claim azp, паттерн внутренних эндпоинтов order-service).
 */
@RestController
@RequestMapping("/delivery/api/v1")
@RequiredArgsConstructor
@Log4j2
public class InternalCourierController {

    private final DeliveryService deliveryService;

    /**
     * GET /couriers/{subId}/active-deliveries-count — сколько АКТИВНЫХ доставок у курьера
     * (по его Keycloak sub). Вызывает auth-service перед удалением аккаунта: >0 → удаление
     * запрещено (доставку числил бы удалённый пользователь).
     */
    @Hidden
    @GetMapping("/couriers/{subId}/active-deliveries-count")
    public ResponseEntity<Long> activeDeliveriesCount(@PathVariable UUID subId,
                                                      JwtAuthenticationToken authentication) {
        requireAuthServiceClient(authentication);
        return new ResponseEntity<>(deliveryService.activeDeliveriesCount(subId), HttpStatus.OK);
    }

    /** Service-to-service авторизация: только сервисный токен клиента auth-service-client (claim azp). */
    private void requireAuthServiceClient(JwtAuthenticationToken authentication) {
        String azp = authentication != null && authentication.getToken() != null
                ? authentication.getToken().getClaimAsString("azp") : null;
        if (!"auth-service-client".equals(azp)) {
            log.warn("InternalCourierController: доступ отклонён, azp={}", azp);
            throw new OrderAccessDeniedException("Ошибка: доступ разрешён только внутренним сервисам");
        }
    }
}
