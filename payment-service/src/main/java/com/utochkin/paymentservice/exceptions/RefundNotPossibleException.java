package com.utochkin.paymentservice.exceptions;

public class RefundNotPossibleException extends RuntimeException {
    public RefundNotPossibleException() {
        super("Ошибка: возврат невозможен!");
    }

    public RefundNotPossibleException(String message) {
        super(message);
    }
}
