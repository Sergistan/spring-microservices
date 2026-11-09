package com.utochkin.orderservice.exceptions;

public class FailedPayOrderException extends RuntimeException {
    public FailedPayOrderException() {
        super("Ошибка: заказ не оплачен, проверьте баланс карты!");
    }
}
