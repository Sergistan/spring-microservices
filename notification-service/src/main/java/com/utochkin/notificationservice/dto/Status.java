package com.utochkin.notificationservice.dto;

/**
 * Статусы заказа из событий topic-orders. DELIVERING/DELIVERED публикует order-service
 * по командам delivery-service (T+5 минут после оплаты — курьер в пути; вручён покупателю).
 */
public enum Status {
    SUCCESS,
    FAILED,
    REFUNDED,
    DELIVERING,
    DELIVERED
}
