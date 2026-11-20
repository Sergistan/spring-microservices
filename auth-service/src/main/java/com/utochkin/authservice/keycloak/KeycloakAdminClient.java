package com.utochkin.authservice.keycloak;

import com.utochkin.authservice.keycloak.KeycloakCreateUserRequest.Credential;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Тонкая обёртка над Keycloak Admin REST API (base-url из keycloak.admin.base-url).
 * Каждый вызов идёт с сервисным токеном (ServiceTokenClient, grant client_credentials —
 * сервисный аккаунт клиента auth-service-client с ролями realm-management: manage-users,
 * view-users, view-clients). При 401 токен инвалидируется и запрос повторяется один раз.
 *
 * Используемые вызовы:
 *  - POST /users                                    — создать пользователя (201 + Location с id);
 *  - GET  /users?username=..&exact=true             — найти id по логину (fallback, если Location пуст);
 *  - GET  /clients?clientId=spring-microservices    — UUID целевого клиента (для назначения client-role);
 *  - GET  /clients/{cu}/roles/{role}                — получить client-role (id+name);
 *  - POST /users/{id}/role-mappings/clients/{cu}    — назначить роль пользователю;
 *  - DELETE /users/{id}                             — удалить пользователя.
 */
@Component
@Log4j2
public class KeycloakAdminClient {

    private static final ParameterizedTypeReference<List<KeycloakUser>> USER_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<KeycloakClient>> CLIENT_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final ServiceTokenClient serviceTokenClient;
    private final RestClient restClient;
    private final String targetClientId;

    // @Autowired явно указывает Spring использовать этот конструктор (в классе есть
    // второй пакетный конструктор для тестов — без аннотации Spring не выбирает между ними)
    @Autowired
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

    // для тестов (MockRestServiceServer привязывается к builder'у RestClient)
    KeycloakAdminClient(ServiceTokenClient serviceTokenClient, RestClient restClient, String targetClientId) {
        this.serviceTokenClient = serviceTokenClient;
        this.restClient = restClient;
        this.targetClientId = targetClientId;
    }

    /** Целевой клиент, в котором живут роли проекта (ADMIN/USER/COURIER) — для назначения ролей. */
    public String targetClientId() {
        return targetClientId;
    }

    /**
     * Создать пользователя с паролем (enabled, emailVerified). Возвращает id нового пользователя
     * из заголовка Location; если Keycloak не вернул Location — ищет id по username (exact).
     * 409 (username/email занят) пробрасывается наружу как HttpClientErrorException.Conflict.
     */
    public String createUser(String username, String email, String firstName, String lastName, String password) {
        KeycloakCreateUserRequest request = new KeycloakCreateUserRequest(
                username, email, firstName, lastName, true, true, List.of(Credential.password(password)));
        try {
            return postUser(request, serviceTokenClient.getServiceToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            serviceTokenClient.invalidate();
            return postUser(request, serviceTokenClient.getServiceToken());
        }
    }

    private String postUser(KeycloakCreateUserRequest request, String token) {
        ResponseEntity<Void> response = restClient.post()
                .uri("/users")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        URI location = response.getHeaders().getLocation();
        if (location != null) {
            return lastPathSegment(location);
        }
        return findUserIdByUsername(request.username())
                .orElseThrow(() -> new IllegalStateException(
                        "Keycloak создал пользователя " + request.username() + ", но id получить не удалось"));
    }

    /** Найти id пользователя по логину (exact=true — точное совпадение). */
    public Optional<String> findUserIdByUsername(String username) {
        try {
            return findUser(username, serviceTokenClient.getServiceToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            serviceTokenClient.invalidate();
            return findUser(username, serviceTokenClient.getServiceToken());
        }
    }

    private Optional<String> findUser(String username, String token) {
        List<KeycloakUser> users = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/users")
                        .queryParam("username", username)
                        .queryParam("exact", "true")
                        .build())
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(USER_LIST);
        return users == null ? Optional.empty()
                : users.stream().map(KeycloakUser::id).findFirst();
    }

    /** Удалить пользователя по id (= sub его JWT). true — удалён; false — не найден (404). */
    public boolean deleteUser(String userId) {
        try {
            return deleteUser(userId, serviceTokenClient.getServiceToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            serviceTokenClient.invalidate();
            return deleteUser(userId, serviceTokenClient.getServiceToken());
        }
    }

    private boolean deleteUser(String userId, String token) {
        try {
            restClient.delete()
                    .uri("/users/{id}", userId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    /** UUID клиента targetClientId (роли проекта — client roles этого клиента). */
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

    /** Получить client-role по имени (например, USER или COURIER) — нужен id+name для маппинга. */
    public KeycloakRole getClientRole(String clientUuid, String roleName) {
        try {
            return getClientRole(clientUuid, roleName, serviceTokenClient.getServiceToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            serviceTokenClient.invalidate();
            return getClientRole(clientUuid, roleName, serviceTokenClient.getServiceToken());
        }
    }

    private KeycloakRole getClientRole(String clientUuid, String roleName, String token) {
        return restClient.get()
                .uri("/clients/{cu}/roles/{role}", clientUuid, roleName)
                .headers(h -> h.setBearerAuth(token))
                .retrieve()
                .body(KeycloakRole.class);
    }

    /** Назначить client-role пользователю (POST role-mappings; идемпотентен на стороне Keycloak). */
    public void assignClientRole(String userId, String clientUuid, KeycloakRole role) {
        try {
            assignClientRole(userId, clientUuid, role, serviceTokenClient.getServiceToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            serviceTokenClient.invalidate();
            assignClientRole(userId, clientUuid, role, serviceTokenClient.getServiceToken());
        }
    }

    private void assignClientRole(String userId, String clientUuid, KeycloakRole role, String token) {
        restClient.post()
                .uri("/users/{id}/role-mappings/clients/{cu}", userId, clientUuid)
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    private static String lastPathSegment(URI uri) {
        String path = uri.getPath();
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /** Минимальный ответ GET /clients?clientId=... — нужен только id. */
    public record KeycloakClient(String id) {
    }
}
