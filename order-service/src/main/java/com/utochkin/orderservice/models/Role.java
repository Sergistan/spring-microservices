package com.utochkin.orderservice.models;

public enum Role {
    /** Администратор: управление каталогом товаров (POST/PUT/DELETE /shop/api/v1/products). */
    ADMIN,
    /** Обычный покупатель: создаёт, оплачивает и возвращает свои заказы. */
    USER,
    /** Курьер доставки (client role COURIER в Keycloak, назначается при саморегистрации через auth-service). */
    COURIER
}
