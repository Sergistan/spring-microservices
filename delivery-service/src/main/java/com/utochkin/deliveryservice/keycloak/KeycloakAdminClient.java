package com.utochkin.deliveryservice.keycloak;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Обёртка над Keycloak Admin REST API — ТОЛЬКО ЧТЕНИЕ (сервисный токен клиента
 * delivery-service-client с ролями realm-management: view-users, view-clients).
 * Используется CourierSyncJob для получения списка курьеров (пользователей с
 * client-role COURIER). При 401 токен инвалидируется и запрос повторяется один раз.
 */
@Component
@Log4j2
public class KeycloakAdminClient {

    private static final ParameterizedTypeReference<List<KeycloakClient>> CLIENT_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<KeycloakUser>> USER_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final ServiceTokenClient serviceTokenClient;
    private final RestClient restClient;
    private final String targetClientId;

    public KeycloakAdminClient(ServiceTokenClient serviceTokenClient,
                               @Value("${keycloak.admin.base-url:http://keycloak:8080/admin/realms/microservices-realm}") String baseUrl,
                               @Value("${keycloak.admin.target-client-id:spring-microservices}") String targetClientId,
                               @Value("${keycloak.admin.connect-timeout-ms:5000}") int connectTimeoutMs,
                               @Value("${keycloak.admin.read-timeout-ms:10000}") int readTimeoutMs) {
        this.serviceTokenClient = serviceTokenClient;
        this.targetClientId = targetClientId;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** UUID клиента targetClientId (роль COURIER живёт среди его client roles). */
    public String getClientUuid() {
        try {
            return getClientUuid(serviceTokenClient.getServiceToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            serviceTokenClient.invalidate();
            return getClientUuid(serviceTokenClient.getServiceToken());
        }
    }

    private String getClientUuid(String token) {
        List<KeycloakClient> clients = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/clients")
                        .queryParam("clientId", targetClientId)
                        .build())
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(CLIENT_LIST);
        if (clients == null || clients.isEmpty()) {
            throw new IllegalStateException("Клиент " + targetClientId + " не найден в realm");
        }
        return clients.get(0).id();
    }

    /** Все пользователи с client-role roleName (например, COURIER) — «существующие курьеры». */
    public List<KeycloakUser> getUsersWithClientRole(String clientUuid, String roleName) {
        try {
            return getUsersWithClientRole(clientUuid, roleName, serviceTokenClient.getServiceToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            serviceTokenClient.invalidate();
            return getUsersWithClientRole(clientUuid, roleName, serviceTokenClient.getServiceToken());
        }
    }

    private List<KeycloakUser> getUsersWithClientRole(String clientUuid, String roleName, String token) {
        List<KeycloakUser> users = restClient.get()
                .uri("/clients/{cu}/roles/{role}/users", clientUuid, roleName)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(USER_LIST);
        return users != null ? users : List.of();
    }

    /** Минимальный ответ GET /clients?clientId=... — нужен только id. */
    public record KeycloakClient(String id) {
    }
}
