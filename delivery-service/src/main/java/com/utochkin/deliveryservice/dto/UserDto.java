package com.utochkin.deliveryservice.dto;

/** Пользователь-владелец заказа из события (для delivery важен как контекст, email — на будущее). */
public record UserDto(String username,
                      String firstName,
                      String lastName,
                      String email) {
}
