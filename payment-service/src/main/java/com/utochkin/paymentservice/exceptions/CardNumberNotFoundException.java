package com.utochkin.paymentservice.exceptions;

public class CardNumberNotFoundException extends RuntimeException {
    public CardNumberNotFoundException() {
        super("Error: card number not found!");
    }
}