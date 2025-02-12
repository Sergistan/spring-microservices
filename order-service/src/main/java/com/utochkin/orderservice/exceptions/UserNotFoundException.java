package com.utochkin.orderservice.exceptions;

public class UserNotFoundException extends RuntimeException{
    public UserNotFoundException() {
        super("Error: user not found!");
    }
}
