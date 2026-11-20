package com.utochkin.orderservice.exceptions;

import com.utochkin.orderservice.models.Status;

/**
 * Конфликт операции доставки с текущим статусом заказа: delivery-service запросил
 * перевод в DELIVERING/DELIVERED, но заказ уже в другом статусе (например, REFUNDED —
 * покупатель успел сделать возврат в 5-минутном окне). HTTP 409: доставка должна
 * отменить свою сторону (CANCELLED + освободить курьера).
 */
public class DeliveryConflictException extends RuntimeException {
    public DeliveryConflictException(Status currentStatus) {
        super("Заказ находится в статусе " + currentStatus + " — операция доставки невозможна");
    }
}
