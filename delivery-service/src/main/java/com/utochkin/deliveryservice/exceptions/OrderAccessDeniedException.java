package com.utochkin.deliveryservice.exceptions;

/** Доступ к внутреннему эндпоинту с чужим azp (не сервисный токен ожидаемого клиента) — 403. */
public class OrderAccessDeniedException extends RuntimeException {
    public OrderAccessDeniedException(String message) {
        super(message);
    }
}
