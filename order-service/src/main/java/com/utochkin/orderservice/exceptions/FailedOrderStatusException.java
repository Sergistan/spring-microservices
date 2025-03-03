package com.utochkin.orderservice.exceptions;

public class FailedOrderStatusException extends RuntimeException {
    public FailedOrderStatusException(String message) {
        super(message);
    }
}
