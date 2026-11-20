package com.utochkin.deliveryservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Внутренние команды доставки в order-service (закрыты azp-гвардом: ждут сервисный
 * токен клиента delivery-service-client — его подставляет ServiceTokenRequestInterceptor).
 * POST — CAS SUCCESS→DELIVERING (T+5 мин после оплаты; в теле — ФИО курьера для письма),
 * PATCH — CAS DELIVERING→DELIVERED. Ответ 409 = заказ уже не в ожидаемом статусе
 * (возврат успел раньше) — delivery отменяет свою сторону.
 */
@FeignClient(name = "order-service", path = "/order/api/v1")
public interface OrderServiceClient {

    @PostMapping("/orders/{orderUuid}/deliveries")
    void startDelivery(@PathVariable("orderUuid") UUID orderUuid, @RequestBody CourierInfo courierInfo);

    @PatchMapping("/orders/{orderUuid}/deliveries")
    void completeDelivery(@PathVariable("orderUuid") UUID orderUuid);
}
