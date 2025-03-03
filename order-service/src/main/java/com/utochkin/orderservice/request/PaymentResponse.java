package com.utochkin.orderservice.request;

import com.utochkin.orderservice.models.Status;
import lombok.Data;

import java.util.UUID;

@Data
public class PaymentResponse {
    private UUID paymentId;
    private Status status;
}
