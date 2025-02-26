package com.utochkin.orderservice.exceptions;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String msg) {
        super(msg);
    }
}
