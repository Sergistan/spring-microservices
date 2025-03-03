package com.utochkin.orderservice.request;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class AccountRequest {
    private Double totalAmount;
    private String cardNumber;
}
