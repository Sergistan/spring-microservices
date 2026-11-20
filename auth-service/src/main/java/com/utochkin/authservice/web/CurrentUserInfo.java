package com.utochkin.authservice.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ответ GET /auth/api/v1/users/me — данные текущего залогиненного пользователя
 * (из доверенных заголовков X-User-* гейтвея). Позволяет перед необратимым
 * DELETE /users/me убедиться, ЧЕЙ именно аккаунт будет удалён.
 */
@Schema(description = "Текущий пользователь (кто авторизован и кто удалится через DELETE /users/me)")
public record CurrentUserInfo(
        @Schema(description = "UUID пользователя в Keycloak (= sub в JWT)", example = "04abe9fc-c31f-4a94-8724-56e4ac3d9e10")
        String sub,
        @Schema(description = "Логин", example = "courier1")
        String username,
        @Schema(description = "Имя", example = "Mikhail")
        String firstName,
        @Schema(description = "Фамилия", example = "Kurierov")
        String lastName,
        @Schema(description = "Электронная почта", example = "mikhail@delivery.local")
        String email,
        @Schema(description = "Роль (первая client role из JWT)", example = "COURIER")
        String role
) {
}
