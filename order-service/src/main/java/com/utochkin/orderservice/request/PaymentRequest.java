package com.utochkin.orderservice.request;

import lombok.Data;

import java.util.UUID;

@Data
public class PaymentRequest {
    private UUID orderUuid;
    private String cardNumber;
}
