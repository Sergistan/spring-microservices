package com.utochkin.authservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Внутренний вызов delivery-service: количество активных доставок курьера перед
 * удалением аккаунта. GET /delivery/api/v1/couriers/{subId}/active-deliveries-count закрыт
 * azp-гвардом (ждёт сервисный токен auth-service-client — подставляет ServiceTokenRequestInterceptor).
 * Разрешение имени — через Eureka.
 */
@FeignClient(name = "delivery-service", path = "/delivery/api/v1")
public interface DeliveryServiceClient {

    /** Сколько SEARCHING_COURIER/ASSIGNED/IN_DELIVERY доставок у курьера с данным Keycloak sub. */
    @GetMapping("/couriers/{subId}/active-deliveries-count")
    Long activeDeliveriesCount(@PathVariable("subId") String subId);
}
