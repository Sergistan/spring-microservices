package com.utochkin.deliveryservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Стандартное тело ошибки ответов delivery-service (поле messageError). */
@Data
@AllArgsConstructor
@Schema(description = "Возвращаемая ошибка")
public class ErrorResponse {
    @Schema(description = "Возвращаемая ошибка", example = "Error", type = "string")
    private String messageError;
}
