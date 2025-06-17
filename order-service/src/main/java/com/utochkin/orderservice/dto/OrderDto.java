package com.utochkin.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.request.OrderRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Schema(description = "Созданный заказ")
public record OrderDto(
        @Schema(description = "UUID заказа", example = "3f9edc4b-e4cf-4257-a485-72a147a0b45f", type = "string", format = "uuid") UUID orderUuid,
        @Schema(description = "Общая стоимость заказа", example = "1000.0", type = "number", format = "double") Double totalAmount,
        @Schema(description = "Статус заказа", example = "SUCCESS", allowableValues = {"WAITING_FOR_PAYMENT", "SUCCESS", "FAILED", "REFUNDED"}) Status orderStatus,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
        @Schema(description = "Дата и время создания заказа", example = "2025-01-12 13:56",
                type = "string", pattern = "yyyy-MM-dd HH:mm") LocalDateTime createdAt,
        @Schema(description = "Адрес доставки", implementation = AddressDto.class) AddressDto addressDto,
        @Schema(description = "Информация о пользователе", implementation = UserDto.class) UserDto userDto,
        @Schema(description = "Список заказанных товаров", implementation = OrderRequest.class) List<OrderRequest> orderRequests) {
}
