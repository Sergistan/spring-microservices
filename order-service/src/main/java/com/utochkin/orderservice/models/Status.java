package com.utochkin.orderservice.models;

public enum Status {
    WAITING_FOR_PAYMENT,
    PROCESSING,
    SUCCESS,
    FAILED,
    REFUNDED
}
