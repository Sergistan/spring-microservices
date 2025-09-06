package com.utochkin.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.request.OrderRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@Schema(description = "Оплаченный заказ")
public class OrderDtoForKafka implements Serializable {
    @Schema(description = "UUID заказа", example = "3f9edc4b-e4cf-4257-a485-72a147a0b45f", type = "string", format = "uuid")
    private final UUID orderUuid;

    @Schema(description = "Общая стоимость заказа", example = "1000.0", type = "number", format = "double")
    private final Double totalAmount;

    @Schema(description = "Статус заказа", example = "SUCCESS", allowableValues = {"WAITING_FOR_PAYMENT", "SUCCESS", "FAILED", "REFUNDED"})
    private final Status orderStatus;

    @Schema(description = "Дата и время создания заказа", example = "2025-01-12 13:56", type = "string", pattern = "yyyy-MM-dd HH:mm")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private final LocalDateTime createdAt;

    @Schema(description = "Дата и время обновления заказа", example = "2025-01-12 13:57", type = "string", pattern = "yyyy-MM-dd HH:mm")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;

    @Schema(description = "Адрес доставки", implementation = AddressDto.class)
    private final AddressDto addressDto;

    @Schema(description = "Информация о пользователе", implementation = UserDto.class)
    private final UserDto userDto;

    @Schema(description = "Список заказанных товаров", implementation = OrderRequest.class)
    private final List<OrderRequest> orderRequests;

    @Schema(description = "UUID оплаты заказа", example = "3f9edc4b-e4cf-4257-a485-72a147a01245f", type = "string", format = "uuid")
    private UUID paymentId;
}
