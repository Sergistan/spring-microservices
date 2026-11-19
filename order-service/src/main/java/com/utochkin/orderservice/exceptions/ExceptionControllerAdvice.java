package com.utochkin.orderservice.exceptions;

import com.utochkin.orderservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionControllerAdvice {
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(ServiceUnavailableException.class)
    public ErrorResponse handlerServiceUnavailableException(ServiceUnavailableException serviceUnavailableException) {
        return new ErrorResponse(serviceUnavailableException.getMessage());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(OrderNotFoundException.class)
    public ErrorResponse handlerOrderNotFoundException(OrderNotFoundException orderNotFoundException) {
        return new ErrorResponse(orderNotFoundException.getMessage());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(UserNotFoundException.class)
    public ErrorResponse handlerUserNotFoundException(UserNotFoundException userNotFoundException) {
        return new ErrorResponse(userNotFoundException.getMessage());
    }

    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    @ExceptionHandler(FailedPayOrderException.class)
    public ErrorResponse handlerFailedPayOrderException(FailedPayOrderException failedPayOrderException) {
        return new ErrorResponse(failedPayOrderException.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(FailedOrderStatusException.class)
    public ErrorResponse handlerFailedOrderStatusException(FailedOrderStatusException failedOrderStatusException) {
        return new ErrorResponse(failedOrderStatusException.getMessage());
    }
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(CardNumberNotFoundException.class)
    public ErrorResponse handlerCardNumberNotFoundException(CardNumberNotFoundException cardNumberNotFoundException) {
        return new ErrorResponse(cardNumberNotFoundException.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ProductUnavailableException.class)
    public ErrorResponse handlerProductUnavailableException(ProductUnavailableException productUnavailableException) {
        return new ErrorResponse(productUnavailableException.getMessage());
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(OrderAccessDeniedException.class)
    public ErrorResponse handlerOrderAccessDeniedException(OrderAccessDeniedException orderAccessDeniedException) {
        return new ErrorResponse(orderAccessDeniedException.getMessage());
    }
}
