package com.utochkin.paymentservice.dto;

import com.utochkin.paymentservice.models.Status;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Ответ платежа/возврата: paymentId + статус (SUCCESS/FAILED/REFUNDED). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResponse {
    @NotEmpty
    private UUID paymentId;
    private Status status;
}
