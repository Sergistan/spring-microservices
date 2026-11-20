package com.utochkin.authservice.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Тело POST /users (создание пользователя в Keycloak). Пароль передаётся как
 * credentials-элемент с temporary=false — пользователь логинится им сразу,
 * без письма «задайте пароль» (email-верификация для учебного realm выключена).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakCreateUserRequest(
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        @JsonProperty("emailVerified") boolean emailVerified,
        List<Credential> credentials
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Credential(String type, String value, boolean temporary) {
        public static Credential password(String value) {
            return new Credential("password", value, false);
        }
    }
}
