package com.utochkin.orderservice.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeignUserTokenRelayInterceptorTest {

    private final ServiceTokenClient serviceTokenClient = mock(ServiceTokenClient.class);
    private final FeignUserTokenRelayInterceptor interceptor = new FeignUserTokenRelayInterceptor(serviceTokenClient);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("apply: в контексте пользовательский JWT -> релеится он, сервисный токен не запрашивается")
    void relaysUserTokenWhenPresent() {
        Jwt jwt = Jwt.withTokenValue("user-token")
                .header("alg", "none")
                .subject("user-sub")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer user-token");
        verifyNoInteractions(serviceTokenClient);
    }

    @Test
    @DisplayName("apply: контекст пуст (фоновый поток) -> подставляется сервисный токен")
    void fallsBackToServiceTokenInBackgroundThread() {
        when(serviceTokenClient.getServiceToken()).thenReturn("svc-token");

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer svc-token");
    }

    @Test
    @DisplayName("apply: сервисный токен недоступен -> запрос уходит без Authorization")
    void sendsWithoutAuthorizationWhenServiceTokenUnavailable() {
        when(serviceTokenClient.getServiceToken()).thenThrow(new IllegalStateException("нет регистрации"));

        RequestTemplate template = new RequestTemplate();
        template.uri("/payment/api/v1/payments/{uuid}");
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }
}
