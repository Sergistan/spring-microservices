package com.utochkin.getawayserver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterConfigTest {

    private final RateLimiterConfig config = new RateLimiterConfig();

    @Test
    @DisplayName("userKeyResolver: JWT-принципал -> ключ по sub пользователя")
    void userKeyResolver_jwtPrincipal_resolvesBySubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-sub-42")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        JwtAuthenticationToken principal = new JwtAuthenticationToken(jwt, java.util.List.of());

        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getPrincipal()).thenReturn(Mono.just(principal));

        StepVerifier.create(config.userKeyResolver().resolve(exchange))
                .expectNext("user:user-sub-42")
                .verifyComplete();
    }

    @Test
    @DisplayName("userKeyResolver: без принципала -> fallback на IP")
    void userKeyResolver_anonymous_fallsBackToIp() {
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getPrincipal()).thenReturn(Mono.empty());

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(exchange.getRequest()).thenReturn(request);
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("10.0.0.7", 54321));

        StepVerifier.create(config.userKeyResolver().resolve(exchange))
                .assertNext(key -> assertThat(key).isEqualTo("ip:10.0.0.7"))
                .verifyComplete();
    }

    @Test
    @DisplayName("userKeyResolver: бин создаётся и является KeyResolver")
    void userKeyResolver_isKeyResolverBean() {
        KeyResolver resolver = config.userKeyResolver();
        assertThat(resolver).isNotNull();
    }
}
