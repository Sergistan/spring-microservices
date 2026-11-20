package com.utochkin.authservice.exceptions;

import com.utochkin.authservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Глобальный обработчик ошибок auth-service: доменные исключения -> ErrorResponse
 * с понятным текстом (по образцу order-service).
 */
@RestControllerAdvice
public class ExceptionControllerAdvice {

    /** Тело запроса не удалось десериализовать (возникает до контроллера) — понятный 400 вместо заглушки Spring. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResponse handlerHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return new ErrorResponse("Некорректное тело запроса: " + ex.getMostSpecificCause().getMessage());
    }

    /** Ошибки @Valid бина регистрации (некорректный username/email/пароль/роль) — 400 с детализацией по полям. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handlerMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return new ErrorResponse("Ошибка валидации: " + details);
    }

    /** Path/query-переменная не того типа. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ErrorResponse handlerMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse("Некорректное значение параметра '" + ex.getName() + "': ожидается тип "
                + (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "неизвестен"));
    }

    /** Username/email уже заняты в Keycloak — 409 (клиент может выбрать другой логин). */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ErrorResponse handlerUserAlreadyExists(UserAlreadyExistsException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /** Есть незавершённые заказы/доставки — удаление аккаунта запрещено, 400. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ActiveOrdersExistException.class)
    public ErrorResponse handlerActiveOrdersExist(ActiveOrdersExistException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /** Курьер с активными доставками — удаление аккаунта запрещено, 400. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ActiveDeliveriesExistException.class)
    public ErrorResponse handlerActiveDeliveriesExist(ActiveDeliveriesExistException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /** Пользователь не найден в Keycloak (уже удалён?) — 404. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(UserNotFoundException.class)
    public ErrorResponse handlerUserNotFound(UserNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /** Keycloak или order-service недоступны — 503 (повторите позже). */
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(ServiceUnavailableException.class)
    public ErrorResponse handlerServiceUnavailable(ServiceUnavailableException ex) {
        return new ErrorResponse(ex.getMessage());
    }
}
