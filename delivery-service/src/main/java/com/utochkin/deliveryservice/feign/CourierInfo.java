package com.utochkin.deliveryservice.feign;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Тело POST /orders/{uuid}/deliveries: ФИО назначенного курьера — order-service
 * положит его в Kafka-событие DELIVERING (notification пишет его в письме покупателю).
 */
@Schema(description = "Курьер, принявший заказ")
public record CourierInfo(
        @Schema(description = "ФИО курьера", example = "Ivan Kurierov")
        String courierName
) {
}
