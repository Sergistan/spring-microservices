package com.utochkin.paymentservice.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(
        description = "Запрос на оплату заказа",
        example = "{ \"totalAmount\": 1000.0, \"cardNumber\": \"1234 5678 9012 3456\" }"
)
public class AccountRequest {
    @NotNull
    @Positive
    @Schema(description = "Общая стоимость заказа", example = "1000.0", type = "number", format = "double")
    private Double totalAmount;

    @NotEmpty
    @Schema(description = "Номер карты с которой происходит оплата заказа", example = "1234 5678 9012 3456", type = "string")
    @Pattern(
            regexp = "\\d{4} \\d{4} \\d{4} \\d{4}",
            message = "Номер карты должен быть в формате '1234 5678 9012 3456'"
    )
    private String cardNumber;
}
