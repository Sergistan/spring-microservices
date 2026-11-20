package com.utochkin.authservice.exceptions;

/** Keycloak или order-service технически недоступны — операцию стоит повторить позже. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
