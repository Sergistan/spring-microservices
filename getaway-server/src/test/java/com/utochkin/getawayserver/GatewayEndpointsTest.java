package com.utochkin.getawayserver;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@WebFluxTest
@AutoConfigureWebTestClient
@Import({
        GatewayEndpointsTest.TestSecurityConfig.class,
        GatewayEndpointsTest.FakeRouterConfig.class
})

class GatewayEndpointsTest {

    private static String jwtToken;

    @BeforeAll
    static void generateJwt() {
        // Генерируем RSA-пару
        KeyPair kp = Keys.keyPairFor(SignatureAlgorithm.RS256);
        PublicKey pub = kp.getPublic();
        PrivateKey priv = kp.getPrivate();

        // Сериализуем publicKey и передадим его в TestSecurityConfig через spEL
        String b64pub = Base64.getEncoder().encodeToString(pub.getEncoded());
        System.setProperty("jwt.public-key",
                "-----BEGIN PUBLIC KEY-----\n" +
                        chunk(b64pub) +
                        "-----END PUBLIC KEY-----"
        );

        // Собираем JWT
        jwtToken = Jwts.builder()
                .setSubject("123")
                .claim("preferred_username", "testuser")
                .claim("given_name", "Test")
                .claim("family_name", "User")
                .claim("email", "test@example.com")
                .claim("resource_access", Map.of(
                        "spring-microservices", Map.of("roles", List.of("user"))
                ))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(priv, SignatureAlgorithm.RS256)
                .compact();
    }

    @Autowired
    WebTestClient client;

    @Test
    @DisplayName("Swagger доступен без аутентификации")
    void swaggerAccessibleWithoutAuth() {
        client.get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("SWAGGER-UI");
    }

    @Test
    @DisplayName("order/api без jwt кидает 401 ошибку")
    void orderApiRequiresAuth() {
        client.get().uri("/order/api/v1/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("order/api с jwt доступен")
    void orderApiWithJwtToken() {
        client.get().uri("/order/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("[]");
    }

    // Разбить длинную Base64-строку на строки по 64 символа
    private static String chunk(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += 64) {
            sb.append(s, i, Math.min(s.length(), i + 64)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Тестовая SecurityConfig: две цепочки
     */
    @Configuration
    @EnableWebFluxSecurity
    static class TestSecurityConfig {
        // Swagger — открыто
        @Bean
        @Order(1)
        public SecurityWebFilterChain swaggerChain(ServerHttpSecurity http) {
            http.securityMatcher(pathMatchers("/swagger-ui.html"))
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(ex -> ex.anyExchange().permitAll());
            return http.build();
        }

        // Order API — JWT
        @Bean
        @Order(2)
        public SecurityWebFilterChain orderChain(ServerHttpSecurity http,
                                                 ReactiveJwtDecoder jwtDecoder) {
            http.securityMatcher(pathMatchers("/order/api/v1/**"))
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(ex -> ex.anyExchange().authenticated())
                    .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtDecoder(jwtDecoder)));
            return http.build();
        }

        // JWT decoder из статического ключа
        @Bean
        public ReactiveJwtDecoder jwtDecoder(
                @Value("${jwt.public-key}") String publicKeyPem) throws Exception {
            String key = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(key);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(spec);
            return NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) pub).build();
        }
    }

    /**
     * В тесте вместо Gateway прокидываем простые маршруты через RouterFunction
     */
    @Configuration
    static class FakeRouterConfig {
        @Bean
        public RouterFunction<ServerResponse> router() {
            return route()
                    .GET("/swagger-ui.html", req -> ServerResponse.ok().bodyValue("SWAGGER-UI"))
                    .GET("/order/api/v1/orders", req -> ServerResponse.ok().bodyValue("[]"))
                    .build();
        }
    }
}