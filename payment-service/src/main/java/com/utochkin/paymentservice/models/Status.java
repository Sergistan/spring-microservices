package com.utochkin.paymentservice.models;

/** Исход платежа: SUCCESS — успех, FAILED — не хватило средств, REFUNDED — возврат выполнен. */
public enum Status {
    SUCCESS,
    FAILED,
    REFUNDED
}
