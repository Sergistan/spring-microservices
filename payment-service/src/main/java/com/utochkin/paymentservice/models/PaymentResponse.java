package com.utochkin.paymentservice.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Ответ после попытки оплаты заказа")
public class PaymentResponse {
    @NotEmpty
    @Schema(description = "UUID заказа", example = "3f9edc4b-e4cf-4257-a485-72a147a0b45f", type = "string", format = "uuid")
    private UUID paymentId;
    @Schema(description = "Статус заказа", example = "SUCCESS", allowableValues = {"WAITING_FOR_PAYMENT", "SUCCESS", "FAILED", "REFUNDED"})
    private Status status;
}
