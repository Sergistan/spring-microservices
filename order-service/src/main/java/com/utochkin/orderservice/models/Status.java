package com.utochkin.orderservice.models;

public enum Status {
    WAITING_FOR_PAYMENT,
    SUCCESS,
    CANCELED,
    REFUNDED,
    FAILED
}
