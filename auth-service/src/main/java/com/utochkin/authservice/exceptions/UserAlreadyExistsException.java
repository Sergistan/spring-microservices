package com.utochkin.authservice.exceptions;

/** Username или email уже заняты в Keycloak (Admin API ответил 409). */
public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException() {
        super("Пользователь с таким username или email уже существует");
    }
}
