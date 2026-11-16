package com.utochkin.orderservice.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@Log4j2
public class ServiceTokenClient {

    static final String REGISTRATION_ID = "keycloak-service";
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);
    private static final Duration FALLBACK_TTL = Duration.ofMinutes(4);

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final Clock clock;

    private volatile CachedToken cachedToken;

    private record CachedToken(String tokenValue, Instant expiresAt) {
        boolean isExpired(Clock clock) {
            return !clock.instant().isBefore(expiresAt);
        }
    }

    @Autowired
    public ServiceTokenClient(ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) {
        ClientRegistrationRepository repository = clientRegistrationRepository.getIfAvailable();
        this.authorizedClientManager = repository != null
                ? new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        repository, new InMemoryOAuth2AuthorizedClientService(repository))
                : null;
        this.clock = Clock.systemUTC();
    }
    // для тестов
    ServiceTokenClient(OAuth2AuthorizedClientManager authorizedClientManager, Clock clock) {
        this.authorizedClientManager = authorizedClientManager;
        this.clock = clock;
    }

    public synchronized String getServiceToken() {
        if (authorizedClientManager == null) {
            throw new IllegalStateException(
                    "ServiceTokenClient: регистрации '" + REGISTRATION_ID + "' нет в конфигурации — проверьте spring.security.oauth2.client в config-repo");
        }

        CachedToken cached = cachedToken;
        if (cached != null && !cached.isExpired(clock)) {
            return cached.tokenValue();
        }

        OAuth2AuthorizedClient client = authorizedClientManager.authorize(OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal("order-service-background")
                .build());

        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException(
                    "ServiceTokenClient: не удалось получить сервисный токен (registration '" + REGISTRATION_ID
                            + "') — проверьте доступность Keycloak и client_credentials клиента");
        }

        Instant expiresAt = client.getAccessToken().getExpiresAt();
        cachedToken = new CachedToken(
                client.getAccessToken().getTokenValue(),
                expiresAt != null ? expiresAt.minus(EXPIRY_SKEW) : clock.instant().plus(FALLBACK_TTL));

        log.debug("ServiceTokenClient: получен новый сервисный токен (registration '{}')", REGISTRATION_ID);
        return cachedToken.tokenValue();
    }
}
