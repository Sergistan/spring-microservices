package com.utochkin.orderservice.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class AccountRequest {
    @NotEmpty
    private Double totalAmount;

    @NotEmpty
    @Pattern(
            regexp = "\\d{4} \\d{4} \\d{4} \\d{4}",
            message = "Номер карты должен быть в формате '5078 6038 0721 8893'"
    )
    private String cardNumber;
}
