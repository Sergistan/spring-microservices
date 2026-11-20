package com.utochkin.deliveryservice.exceptions;

import com.utochkin.deliveryservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Обработчик ошибок delivery-service: доменные исключения -> ErrorResponse с понятным текстом. */
@RestControllerAdvice
public class ExceptionControllerAdvice {

    /** Доступ к внутреннему эндпоинту с чужим azp (не сервисный токен ожидаемого клиента) — 403. */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(OrderAccessDeniedException.class)
    public ErrorResponse handlerOrderAccessDenied(OrderAccessDeniedException ex) {
        return new ErrorResponse(ex.getMessage());
    }
}
