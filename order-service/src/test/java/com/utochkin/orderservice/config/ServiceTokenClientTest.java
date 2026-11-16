package com.utochkin.orderservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceTokenClientTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private final OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
    private final ServiceTokenClient client = new ServiceTokenClient(manager, Clock.fixed(NOW, ZoneOffset.UTC));

    private OAuth2AuthorizedClient authorizedClient(String tokenValue, Instant expiresAt) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(ServiceTokenClient.REGISTRATION_ID)
                .clientId("order-service-client")
                .tokenUri("http://keycloak:8080/token")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, tokenValue, NOW.minus(Duration.ofMinutes(1)), expiresAt);
        return new OAuth2AuthorizedClient(registration, "order-service-background", accessToken, null);
    }

    @Test
    @DisplayName("getServiceToken: до истечения кэша менеджер вызывается один раз")
    void cachesTokenUntilExpiry() {
        when(manager.authorize(any()))
                .thenReturn(authorizedClient("svc-token-1", NOW.plus(Duration.ofSeconds(60))));

        String first = client.getServiceToken();
        String second = client.getServiceToken();

        assertThat(first).isEqualTo("svc-token-1");
        assertThat(second).isEqualTo("svc-token-1");
        verify(manager, times(1)).authorize(any());
    }

    @Test
    @DisplayName("getServiceToken: после истечения (с запасом 30с) токен перезапрашивается")
    void refetchesTokenAfterExpiry() {
        when(manager.authorize(any()))
                .thenReturn(authorizedClient("svc-token-1", NOW.plus(Duration.ofSeconds(20))))
                .thenReturn(authorizedClient("svc-token-2", NOW.plus(Duration.ofSeconds(60))));

        String first = client.getServiceToken();
        String second = client.getServiceToken();

        assertThat(first).isEqualTo("svc-token-1");
        assertThat(second).isEqualTo("svc-token-2");
        verify(manager, times(2)).authorize(any());
    }

    @Test
    @DisplayName("getServiceToken: менеджер вернул null -> понятное исключение")
    void throwsWhenAuthorizeFails() {
        when(manager.authorize(any())).thenReturn(null);

        assertThatThrownBy(() -> client.getServiceToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("не удалось получить сервисный токен");
    }
}
