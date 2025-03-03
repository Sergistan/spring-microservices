package com.utochkin.paymentservice.exceptions;

import com.utochkin.paymentservice.requests.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionControllerAdvice {
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(CardNumberNotFoundException.class)
    public ErrorResponse handlerCardNumberNotFoundException(CardNumberNotFoundException cardNumberNotFoundException) {
        return new ErrorResponse(cardNumberNotFoundException.getMessage());
    }

    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    @ExceptionHandler(FailedPayOrderException.class)
    public ErrorResponse handlerFailedPayOrderException(FailedPayOrderException failedPayOrderException) {
        return new ErrorResponse(failedPayOrderException.getMessage());
    }
}
