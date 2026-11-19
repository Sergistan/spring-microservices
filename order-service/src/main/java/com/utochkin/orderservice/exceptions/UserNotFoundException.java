package com.utochkin.orderservice.exceptions;

public class UserNotFoundException extends RuntimeException{
    public UserNotFoundException() {
        super("Ошибка: пользователь не найден!");
    }
}
