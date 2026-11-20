package com.utochkin.orderservice.feign;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Тело запроса платежа/возврата в payment-service (Feign): идемпотентный ключ orderUuid,
 * сумма операции и карта списания/зачисления.
 */
@AllArgsConstructor
@Data
public class AccountRequest {
    @NotNull
    private UUID orderUuid;

    @NotNull
    @Positive
    private BigDecimal totalAmount;

    @NotNull
    @Pattern(
            regexp = "\\d{4} \\d{4} \\d{4} \\d{4}",
            message = "Номер карты должен быть в формате '5078 6038 0721 8893'"
    )
    private String cardNumber;
}
