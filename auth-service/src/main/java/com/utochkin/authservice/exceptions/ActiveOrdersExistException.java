package com.utochkin.authservice.exceptions;

/** У пользователя есть незавершённые заказы/доставки — удаление аккаунта запрещено. */
public class ActiveOrdersExistException extends RuntimeException {
    public ActiveOrdersExistException() {
        super("Нельзя удалить аккаунт: есть незавершённые заказы или доставки");
    }
}
