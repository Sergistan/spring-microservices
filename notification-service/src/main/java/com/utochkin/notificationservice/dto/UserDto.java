package com.utochkin.notificationservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

/** Пользователь-владелец заказа из события (email — адресат письма). */
public record UserDto(@NotEmpty String username,
                      @NotEmpty String firstName,
                      @NotEmpty String lastName,
                      @Email String email) {
}
