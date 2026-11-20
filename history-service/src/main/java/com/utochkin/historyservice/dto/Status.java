package com.utochkin.historyservice.dto;

/**
 * Статусы заказа из событий topic-orders. DELIVERING/DELIVERED публикует order-service
 * по командам delivery-service; консьюмер просто дописывает события в историю —
 * значения нужны, чтобы десериализация новых событий не падала.
 */
public enum Status {
    SUCCESS,
    FAILED,
    REFUNDED,
    DELIVERING,
    DELIVERED
}
