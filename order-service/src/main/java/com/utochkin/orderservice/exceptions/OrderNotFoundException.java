package com.utochkin.orderservice.exceptions;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException() {
        super("Error: order not found!");
    }
}
