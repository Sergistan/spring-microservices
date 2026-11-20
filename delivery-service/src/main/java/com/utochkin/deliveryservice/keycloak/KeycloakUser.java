package com.utochkin.deliveryservice.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ответ Admin REST (пользователи с client-role COURIER). id совпадает с claim sub
 * в JWT этого пользователя — по нему сопоставляем курьера в таблице couriers.
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
