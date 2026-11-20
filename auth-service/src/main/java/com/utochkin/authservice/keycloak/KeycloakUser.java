package com.utochkin.authservice.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ответ Admin REST GET /users?username=... — минимальный набор полей пользователя Keycloak.
 * id совпадает с claim sub в JWT этого пользователя.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Пользователь Keycloak")
public record KeycloakUser(
        @Schema(description = "UUID пользователя (= sub в его JWT)") String id,
        @Schema(description = "Логин") String username,
        String email,
        String firstName,
        String lastName
) {
}
