package com.utochkin.orderservice.exceptions;

import com.utochkin.orderservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolationException;

/**
 * Глобальный обработчик ошибок order-service: доменные исключения -> ErrorResponse с понятным текстом.
 */
@RestControllerAdvice
public class ExceptionControllerAdvice {

    /**
     * Тело запроса не удалось десериализовать (возникает ДО входа в контроллер):
     * например, articleId не является корректным UUID. Без этого обработчика Spring
     * отвечает стандартной заглушкой {timestamp, status, error, path}.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ErrorResponse handlerHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return new ErrorResponse("Некорректное тело запроса: " + ex.getMostSpecificCause().getMessage());
    }

    /** Ошибки @Valid бина запроса (тоже до бизнес-логики). */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ErrorResponse handlerMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return new ErrorResponse("Ошибка валидации: " + details);
    }

    /** Path/query-переменная не того типа (например, orderUuid не UUID). */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ErrorResponse handlerMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return new ErrorResponse("Некорректное значение параметра '" + ex.getName() + "': ожидается тип "
                + (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "неизвестен"));
    }

    /**
     * Нарушение Bean Validation при сохранении сущности (например, заказ по сервисному токену
     * client_credentials: у него нет пользовательских claim'ов -> User с пустыми полями).
     * Вместо нечитаемого 500 — понятный 400.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException.class)
    public ErrorResponse handlerConstraintViolation(ConstraintViolationException ex) {
        String details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return new ErrorResponse("Некорректные данные: " + details);
    }
    /** Технический сбой (shop/payment недоступны, исчерпаны fallback-сценарии) → понятный 503 вместо стектрейса. */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(ServiceUnavailableException.class)
    public ErrorResponse handlerServiceUnavailableException(ServiceUnavailableException serviceUnavailableException) {
        return new ErrorResponse(serviceUnavailableException.getMessage());
    }

    /** Заказ по UUID не найден (ошибка в ссылке/повтор после удаления) → 404. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(OrderNotFoundException.class)
    public ErrorResponse handlerOrderNotFoundException(OrderNotFoundException orderNotFoundException) {
        return new ErrorResponse(orderNotFoundException.getMessage());
    }

    /** Локальный пользователь по subId+username не найден (рассинхронизация зеркала) → 404. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(UserNotFoundException.class)
    public ErrorResponse handlerUserNotFoundException(UserNotFoundException userNotFoundException) {
        return new ErrorResponse(userNotFoundException.getMessage());
    }

    /** Платёж отклонён: не хватает средств на карте → 402 PAYMENT_REQUIRED (специальный код для оплаты). */
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    @ExceptionHandler(FailedPayOrderException.class)
    public ErrorResponse handlerFailedPayOrderException(FailedPayOrderException failedPayOrderException) {
        return new ErrorResponse(failedPayOrderException.getMessage());
    }

    /** Бизнес-отказ по статусу заказа: повторная оплата, возврат неоплаченного/доставленного заказа и т.п. → 400. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(FailedOrderStatusException.class)
    public ErrorResponse handlerFailedOrderStatusException(FailedOrderStatusException failedOrderStatusException) {
        return new ErrorResponse(failedOrderStatusException.getMessage());
    }

    /** Карта с таким номером не найдена в payment-service (Feign 404) → 404. */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(CardNumberNotFoundException.class)
    public ErrorResponse handlerCardNumberNotFoundException(CardNumberNotFoundException cardNumberNotFoundException) {
        return new ErrorResponse(cardNumberNotFoundException.getMessage());
    }

    /** Товара нет в магазине или его количества недостаточно (Feign 409/404 от shop) → 400. */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ProductUnavailableException.class)
    public ErrorResponse handlerProductUnavailableException(ProductUnavailableException productUnavailableException) {
        return new ErrorResponse(productUnavailableException.getMessage());
    }

    /** Доступ к чужому заказу (subId не совпадает) или к внутреннему эндпоинту с чужим azp → 403. */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(OrderAccessDeniedException.class)
    public ErrorResponse handlerOrderAccessDeniedException(OrderAccessDeniedException orderAccessDeniedException) {
        return new ErrorResponse(orderAccessDeniedException.getMessage());
    }

    /**
     * Конфликт операции доставки с текущим статусом заказа: delivery-service запросил
     * DELIVERING/DELIVERED, но заказ уже, например, REFUNDED (возврат успел в 5-минутное окно).
     * 409 — сигнал доставки отменить свою сторону (CANCELLED + освободить курьера).
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DeliveryConflictException.class)
    public ErrorResponse handlerDeliveryConflictException(DeliveryConflictException deliveryConflictException) {
        return new ErrorResponse(deliveryConflictException.getMessage());
    }
}
