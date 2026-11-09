package com.utochkin.paymentservice.exceptions;

public class CardNumberNotFoundException extends RuntimeException {
    public CardNumberNotFoundException() {
        super("Ошибка: карта с таким номером не найдена!");
    }
}