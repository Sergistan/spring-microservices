package com.utochkin.getawayserver;

import com.utochkin.getawayserver.config.JwtTokenFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class JwtTokenFilterTest {

    static JwtTokenFilter filter;
    static String publicKeyPem;
    static PrivateKey testPrivateKey;

    public WebTestClient client;

    @BeforeAll
    static void setupKeys() {
        // Генерируем пару RSA для RS256
        KeyPair keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        // Сериализуем publicKey в PEM (для фильтра)
        String base64Pub = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + base64Pub + "\n-----END PUBLIC KEY-----";

        filter = new JwtTokenFilter();
        filter.publicKey = publicKeyPem;

        // Сохраняем приватный ключ для подписи в тестовом поле
        testPrivateKey = privateKey;
    }

    @Test
    @DisplayName("Проверка валидности Headers из Jwt")
    void filterInjectsHeaders() throws Exception {
        // 1) собираем валидный JWT с нужными claims
        String token = Jwts.builder()
                .setSubject("123")
                .claim("preferred_username", "ivan")
                .claim("given_name", "Иван")
                .claim("family_name", "Иванов")
                .claim("email", "ivan@example.com")
                .claim("resource_access", Map.of(
                        "spring-microservices",
                        Map.of("roles", List.of("user"))
                ))
                .setIssuedAt(new Date())
                .signWith(testPrivateKey, SignatureAlgorithm.RS256)
                .compact();

        // 2) готовим exchange с заголовком
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/any/path")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        );

        // 3) цепочка, которая проверит заголовки
        GatewayFilterChainStub chain = new GatewayFilterChainStub();

        Mono<Void> result = filter.filter(exchange, chain);
        StepVerifier.create(result).verifyComplete();

        // 4) проверяем, что в мутированном запросе есть наши X-headers
        var mutated = chain.capturedExchange.getRequest();
        assertThat(mutated.getHeaders().getFirst("X-User-SubId")).isEqualTo("123");
        assertThat(mutated.getHeaders().getFirst("X-User-UserName")).isEqualTo("ivan");
        assertThat(mutated.getHeaders().getFirst("X-User-FirstName"))
                .isEqualTo(URLEncoder.encode("Иван", StandardCharsets.UTF_8));
        assertThat(mutated.getHeaders().getFirst("X-User-LastName"))
                .isEqualTo(URLEncoder.encode("Иванов", StandardCharsets.UTF_8));
        assertThat(mutated.getHeaders().getFirst("X-User-Email")).isEqualTo("ivan@example.com");
        assertThat(mutated.getHeaders().getFirst("X-User-Role")).isEqualTo("user");
    }

    // Простая «заглушка» для GatewayFilterChain
    static class GatewayFilterChainStub implements GatewayFilterChain {
        ServerWebExchange capturedExchange;

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.capturedExchange = exchange;
            return Mono.empty();
        }
    }
}
