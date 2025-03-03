package com.utochkin.shopservice.exceptions;

import com.utochkin.shopservice.requests.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionControllerAdvice {
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(ProductNotFoundException.class)
    public ErrorResponse handlerProductNotFoundException(ProductNotFoundException productNotFoundException) {
        return new ErrorResponse(productNotFoundException.getMessage());
    }
}
