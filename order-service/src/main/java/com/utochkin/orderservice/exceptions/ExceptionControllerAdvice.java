package com.utochkin.orderservice.exceptions;

import com.utochkin.orderservice.models.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionControllerAdvice {
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(ServiceUnavailableException.class)
    public ErrorResponse handlerAccessDeniedException(ServiceUnavailableException serviceUnavailableException) {
        return new ErrorResponse(serviceUnavailableException.getMessage());
    }
}
