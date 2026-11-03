package com.utochkin.orderservice.exceptions;

public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException() {
        super("Товар отсутствует в магазине или его количество недостаточно для заказа");
    }
}
