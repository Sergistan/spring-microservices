package com.utochkin.orderservice.models;

public enum Status {
    STOCK_PENDING,
    WAITING_FOR_PAYMENT,
    PROCESSING,
    SUCCESS,
    FAILED,
    REFUNDED
}
