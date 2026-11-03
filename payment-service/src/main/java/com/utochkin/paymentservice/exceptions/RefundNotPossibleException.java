package com.utochkin.paymentservice.exceptions;

public class RefundNotPossibleException extends RuntimeException {
    public RefundNotPossibleException() {
        super("Error: refund not possible!");
    }

    public RefundNotPossibleException(String message) {
        super(message);
    }
}
