package com.utochkin.authservice.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Тело запроса саморегистрации POST /auth/api/v1/users.
 * Роль ограничена whitelist-ом USER|COURIER: зарегистрироваться как ADMIN нельзя
 * (защита от повышения привилегий), default-значения нет — выбор обязателен.
 */
@Schema(description = "Регистрационные данные нового пользователя")
public record RegisterRequest(
        @Schema(description = "Логин (уникальный в realm)", example = "ivanov")
        @NotBlank(message = "username обязателен")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$", message = "username: 3-50 символов, латиница/цифры/._-")
        String username,

        @Schema(description = "Email (уникальный в realm)", example = "ivanov@mail.ru")
        @NotBlank(message = "email обязателен")
        @Email(message = "некорректный формат email")
        String email,

        @Schema(description = "Имя", example = "Иван")
        @NotBlank(message = "firstName обязателен")
        String firstName,

        @Schema(description = "Фамилия", example = "Иванов")
        @NotBlank(message = "lastName обязателен")
        String lastName,

        @Schema(description = "Пароль (минимум 8 символов)", example = "Str0ngPass!")
        @NotBlank(message = "password обязателен")
        @Size(min = 8, max = 128, message = "пароль: от 8 до 128 символов")
        String password,

        @Schema(description = "Роль при регистрации: USER (покупатель) или COURIER (курьер доставки)",
                example = "USER", allowableValues = {"USER", "COURIER"})
        @NotNull(message = "role обязательна")
        @Pattern(regexp = "USER|COURIER", message = "role может быть только USER или COURIER")
        String role
) {
}
