package com.utochkin.authservice.keycloak;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
public class KeycloakAdminClientTest {

    private static final String BASE = "http://keycloak:8080/admin/realms/microservices-realm";
    private static final String TOKEN = "test-service-token";

    @Mock
    private ServiceTokenClient serviceTokenClient;

    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(serviceTokenClient, builder.build(), "spring-microservices");
        given(serviceTokenClient.getServiceToken()).willReturn(TOKEN);
    }

    @Test
    @DisplayName("createUser: 201 + Location → id из заголовка; запрос идёт с Bearer-токеном")
    void createUser_parsesLocationHeader() {
        server.expect(requestTo(BASE + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withCreatedEntity(URI.create(BASE + "/users/9a82f1c2-1111"))
                        .contentType(MediaType.APPLICATION_JSON));

        String userId = client.createUser("ivanov", "i@mail.ru", "Иван", "Иванов", "pass12345");

        assertThat(userId).isEqualTo("9a82f1c2-1111");
        server.verify();
    }

    @Test
    @DisplayName("createUser: 401 от Admin API → токен инвалидируется и запрос повторяется один раз")
    void createUser_unauthorized_retriesWithFreshToken() {
        server.expect(requestTo(BASE + "/users"))
                .andRespond(withUnauthorizedRequest());
        server.expect(requestTo(BASE + "/users"))
                .andRespond(withCreatedEntity(URI.create(BASE + "/users/abc")));

        String userId = client.createUser("ivanov", "i@mail.ru", "Иван", "Иванов", "pass12345");

        assertThat(userId).isEqualTo("abc");
        verify(serviceTokenClient).invalidate();
        server.verify();
    }

    @Test
    @DisplayName("findUserIdByUsername: exact-поиск, id первого результата")
    void findUserIdByUsername_found() {
        server.expect(requestTo(BASE + "/users?username=ivanov&exact=true"))
                .andRespond(withSuccess("[{\"id\":\"u-1\",\"username\":\"ivanov\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.findUserIdByUsername("ivanov")).contains("u-1");
    }

    @Test
    @DisplayName("deleteUser: 204 → true")
    void deleteUser_success() {
        server.expect(requestTo(BASE + "/users/u-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        assertThat(client.deleteUser("u-1")).isTrue();
    }

    @Test
    @DisplayName("deleteUser: 404 → false (пользователя нет)")
    void deleteUser_notFound() {
        server.expect(requestTo(BASE + "/users/u-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.deleteUser("u-1")).isFalse();
    }

    @Test
    @DisplayName("assignClientRole: POST role-mappings с телом [role]")
    void assignClientRole_postsMapping() {
        server.expect(requestTo(BASE + "/users/u-1/role-mappings/clients/cu-1"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withNoContent());

        client.assignClientRole("u-1", "cu-1", new KeycloakRole("r-1", "COURIER"));

        server.verify();
    }

    @Test
    @DisplayName("getClientUuid: первый элемент списка клиентов по clientId-фильтру")
    void getClientUuid_firstClient() {
        server.expect(requestTo(BASE + "/clients?clientId=spring-microservices"))
                .andRespond(withSuccess("[{\"id\":\"cu-1\",\"clientId\":\"spring-microservices\"}]", MediaType.APPLICATION_JSON));

        assertThat(client.getClientUuid()).isEqualTo("cu-1");
    }
}
