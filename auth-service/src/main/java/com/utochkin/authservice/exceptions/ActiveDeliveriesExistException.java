package com.utochkin.authservice.exceptions;

/** У курьера есть активные доставки — удаление аккаунта запрещено (доставку числил бы удалённый пользователь). */
public class ActiveDeliveriesExistException extends RuntimeException {
    public ActiveDeliveriesExistException() {
        super("Нельзя удалить аккаунт: есть активные доставки");
    }
}
