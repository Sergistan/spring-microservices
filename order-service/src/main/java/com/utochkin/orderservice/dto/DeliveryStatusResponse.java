package com.utochkin.orderservice.dto;

import com.utochkin.orderservice.models.Status;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Ответ внутренних эндпоинтов доставки (POST/PATCH /orders/{uuid}/deliveries):
 * order-service сообщает delivery-service итоговый статус заказа после CAS-перехода.
 */
@Schema(description = "Статус заказа после команды доставки")
public record DeliveryStatusResponse(
        @Schema(description = "UUID заказа", example = "3f9edc4b-e4cf-4257-a485-72a147a0b45f", type = "string", format = "uuid")
        UUID orderUuid,
        @Schema(description = "Итоговый статус заказа", example = "DELIVERING")
        Status orderStatus
) {
}
