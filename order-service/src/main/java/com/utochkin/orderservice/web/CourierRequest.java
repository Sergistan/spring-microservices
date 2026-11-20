package com.utochkin.orderservice.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Тело внутреннего POST /orders/{uuid}/deliveries от delivery-service:
 * ФИО назначенного курьера — попадёт в Kafka-событие DELIVERING (для письма покупателю).
 */
@Schema(description = "Информация о курьере, принявшем заказ")
public record CourierRequest(
        @Schema(description = "ФИО курьера", example = "Ivan Kurierov")
        String courierName
) {
}
