package com.utochkin.authservice.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Ответ GET /clients/{uuid}/roles/{name} — client-role Keycloak.
 * Для назначения роли пользователю требуется и id, и имя роли.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRole(String id, String name) {
}
