package com.utochkin.shopservice.models;

/** Тип операции остатков: DEDUCT — списание при создании заказа, RESTORE — восстановление при возврате. */
public enum StockOperationType {
    DEDUCT,
    RESTORE
}
