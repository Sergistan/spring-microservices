package com.utochkin.deliveryservice.dto;

/**
 * Статусы заказа из событий topic-orders. Для delivery-service значимы SUCCESS
 * (создать доставку) и REFUNDED (отменить); WAITING_FOR_PAYMENT/FAILED игнорируются,
 * DELIVERING/DELIVERED публикует сам order-service по нашим командам (повторно не обрабатываем).
 */
public enum Status {
    WAITING_FOR_PAYMENT,
    SUCCESS,
    FAILED,
    REFUNDED,
    DELIVERING,
    DELIVERED
}
