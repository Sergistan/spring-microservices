package com.utochkin.authservice.exceptions;

/** Пользователь не найден в Keycloak (например, уже удалён). */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("Ошибка: пользователь не найден!");
    }
}
