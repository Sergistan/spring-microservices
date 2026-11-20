package com.utochkin.orderservice.feign;

import com.utochkin.orderservice.models.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Ответ платежа/возврата от payment-service: paymentId + итоговый статус. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponse {
    @NotNull
    @Schema(description = "UUID заказа", example = "3f9edc4b-e4cf-4257-a485-72a147a0b45f", type = "string", format = "uuid")
    private UUID paymentId;

    @Schema(description = "Статус заказа", example = "SUCCESS", allowableValues = {"WAITING_FOR_PAYMENT", "PROCESSING", "SUCCESS", "FAILED", "REFUNDED"})
    private Status status;
}
