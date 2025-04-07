package com.utochkin.shopservice.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Возвращаемая ошибка")
public class ErrorResponse {
    @Schema(description = "Возвращаемая ошибка", example = "Error", type = "string")
    private String messageError;
}
