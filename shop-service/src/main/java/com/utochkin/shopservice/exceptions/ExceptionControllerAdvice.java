package com.utochkin.shopservice.exceptions;

import com.utochkin.shopservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionControllerAdvice {
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ProductNotFoundException.class)
    public ErrorResponse handlerProductNotFoundException(ProductNotFoundException productNotFoundException) {
        return new ErrorResponse(productNotFoundException.getMessage());
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(InsufficientStockException.class)
    public ErrorResponse handlerInsufficientStockException(InsufficientStockException insufficientStockException) {
        return new ErrorResponse(insufficientStockException.getMessage());
    }
}
