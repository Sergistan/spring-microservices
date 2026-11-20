package com.utochkin.getawayserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import reactor.core.publisher.Mono;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

/**
 * Две security-цепочки gateway (WebFlux):
 *  1) @Order(1) oauth2LoginChain — swagger-ui/docs и OAuth2-флоу входа по Keycloak;
 *  2) @Order(2) resourceServerChain — api-маршруты order/shop/auth: только валидный JWT,
 *     кроме анонимной регистрации POST /auth/api/v1/users.
 * /payment/api/v1/** наружу не выставляется (маршрута нет) — payment-service внутренний,
 * как и delivery-service: внешний анонимный/пользовательский доступ к платежам невозможен.
 */
@Configuration
@EnableWebFluxSecurity
public class ReactiveConfig {

    /** Цепочка 1 (@Order(1)): swagger/docs + OAuth2-логин по Keycloak; после входа — редирект в swagger-ui. */
    @Bean
    @Order(1)
    public SecurityWebFilterChain oauth2LoginChain(ServerHttpSecurity http) {
        RedirectServerAuthenticationSuccessHandler successHandler =
                new RedirectServerAuthenticationSuccessHandler("/swagger-ui.html");
        http
                .securityMatcher(pathMatchers(
                        // Swagger UI
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/webjars/swagger-ui/**",
                        "/favicon.ico",
                        // OpenAPI JSON
                        "/v3/api-docs/**",
                        "/order/v3/api-docs**",
                        "/shop/v3/api-docs**",
                        "/auth/v3/api-docs**",
                        // OAuth2 flow
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/login/oauth2/code/**",
                        // Swagger‑UI собственный redirect
                        "/webjars/swagger-ui/oauth2-redirect.html"
                ))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .oauth2Login(oauth2 -> oauth2.authenticationSuccessHandler(successHandler))
                .oauth2Client(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Цепочка 2 (@Order(2)): api-маршруты order/shop/payment/auth — JWT (ресурс-сервер); исключение — анонимная регистрация.
     */
    @Bean
    @Order(2)
    public SecurityWebFilterChain resourceServerChain(ServerHttpSecurity http) {
        // /payment/api/v1/** СОЗНАТЕЛЬНО НЕ входит в маршруты гейтвея: платежные операции дергает
        // только order-service напрямую через Eureka (с релеем пользовательского JWT или сервисным
        // токеном) — прямой вызов платежей из интернета миновал бы проверки владельца/статуса заказа
        // (дыра закрыта). Docs-маршруты (/order/v3/api-docs и т.п.) открыты — метаданные swagger-ui
            // за oauth2Login. /auth/api/v1/**: единственное исключение — POST /auth/api/v1/users
        // (саморегистрация, permitAll на уровне gateway и auth-service; защищено RequestRateLimiter
        // в маршруте), удаление аккаунта DELETE /users/me и всё остальное — только с JWT.
        http
                .securityMatcher(pathMatchers(
                        "/order/api/v1/**",
                        "/shop/api/v1/**",
                        "/auth/api/v1/**"
                ))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/auth/api/v1/users").permitAll()
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt
                                .jwtAuthenticationConverter(grantedAuthoritiesExtractor())
                        )
                );
        return http.build();
    }

    /** Конвертер realm-ролей JWT в Spring-authorities (нужно для hasRole на downstream-сервисах). */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new AuthServerRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

}
