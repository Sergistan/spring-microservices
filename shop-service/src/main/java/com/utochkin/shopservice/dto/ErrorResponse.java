package com.utochkin.shopservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Стандартное тело ошибки всех 4xx/5xx ответов (поле messageError). */
@Data
@AllArgsConstructor
@Schema(description = "Возвращаемая ошибка")
public class ErrorResponse {
    @Schema(description = "Возвращаемая ошибка", example = "Error", type = "string")
    private String messageError;
}
