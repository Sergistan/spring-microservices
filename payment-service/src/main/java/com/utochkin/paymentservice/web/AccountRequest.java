package com.utochkin.paymentservice.web;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Тело внутренних POST /payments и /refunds (вызовы order-service через Feign). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountRequest {
    @NotNull
    private UUID orderUuid;

    @NotNull
    @Positive
    private BigDecimal totalAmount;

    @NotEmpty
    @Pattern(
            regexp = "\\d{4} \\d{4} \\d{4} \\d{4}",
            message = "Номер карты должен быть в формате '1234 5678 9012 3456'"
    )
    private String cardNumber;
}
