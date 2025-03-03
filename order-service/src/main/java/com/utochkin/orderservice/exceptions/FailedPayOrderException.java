package com.utochkin.orderservice.exceptions;

public class FailedPayOrderException extends RuntimeException {
    public FailedPayOrderException() {
        super("Error: the order has not been paid, check the card balance!");
    }
}
