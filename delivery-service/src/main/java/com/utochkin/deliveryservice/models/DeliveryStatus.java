package com.utochkin.deliveryservice.models;

/**
 * Стадии конвейера доставки:
 * SEARCHING_COURIER — свободного курьера пока нет, назначение повторяет scheduler;
 * ASSIGNED — курьер атомарно занят, ждём T+5мин с момента оплаты;
 * IN_DELIVERY — заказ переведён в DELIVERING (окно возврата закрыто), курьер «в пути»;
 * DELIVERED — вручён покупателю, курьер освобождён (терминальный);
 * CANCELLED — покупатель успел вернуть заказ до T+5мин (терминальный).
 */
public enum DeliveryStatus {
    SEARCHING_COURIER,
    ASSIGNED,
    IN_DELIVERY,
    DELIVERED,
    CANCELLED
}
