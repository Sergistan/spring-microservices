package com.utochkin.orderservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CardNumberNotFoundException extends RuntimeException {
    public CardNumberNotFoundException() {
        super("Ошибка: карта с таким номером не найдена!");
    }
}