package com.utochkin.authservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Внутренний вызов order-service: проверка активных заказов пользователя перед
 * удалением аккаунта. GET /order/api/v1/users/{subId}/has-active-orders закрыт
 * azp-гвардом (ждёт сервисный токен клиента auth-service-client — его подставляет
 * ServiceTokenRequestInterceptor). Разрешение имени — через Eureka.
 */
@FeignClient(name = "order-service", path = "/order/api/v1")
public interface OrderServiceClient {

    /** true — есть заказы не в финальных статусах (включая SUCCESS и стадии доставки). */
    @GetMapping("/users/{subId}/has-active-orders")
    Boolean hasActiveOrders(@PathVariable("subId") String subId);
}
