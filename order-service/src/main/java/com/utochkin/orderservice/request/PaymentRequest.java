package com.utochkin.orderservice.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class PaymentRequest {
    @NotEmpty
    @Schema(description = "UUID заказа", example = "3f9edc4b-e4cf-4257-a485-72a147a0b45f", type = "string", format = "uuid")
    private UUID orderUuid;

    @NotEmpty
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
