package com.utochkin.paymentservice.requests;

import lombok.Data;

@Data
public class AccountRequest {
    private Double totalAmount;
    private String cardNumber;
}
