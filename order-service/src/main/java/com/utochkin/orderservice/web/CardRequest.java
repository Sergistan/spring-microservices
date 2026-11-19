package com.utochkin.orderservice.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Тело запроса оплаты/возврата для REST-эндпоинтов /orders/{orderUuid}/payments и /orders/{orderUuid}/refunds:
 * orderUuid идентифицирует заказ в пути, в теле остаётся только карта.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardRequest {

    @NotNull
    @Pattern(
            regexp = "\\d{4} \\d{4} \\d{4} \\d{4}",
            message = "Номер карты должен быть в формате '5078 6038 0721 8893'"
    )
    @Schema(
            description = "Номер карты в формате '5078 6038 0721 8893'",
            example     = "\"5078 6038 0721 8893\"",
            type        = "string"
    )
    private String cardNumber;
}
