package com.utochkin.orderservice.exceptions;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException() {
        super("Ошибка: заказ не найден!");
    }
}
