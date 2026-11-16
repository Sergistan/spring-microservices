package com.utochkin.orderservice.exceptions;

public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException() {
        super("Ошибка: заказ принадлежит другому пользователю");
    }
}
