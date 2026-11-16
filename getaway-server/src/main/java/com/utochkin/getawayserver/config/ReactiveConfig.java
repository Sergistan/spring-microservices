package com.utochkin.getawayserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
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


//@Configuration
//@EnableWebFluxSecurity
//public class ReactiveConfig {
//
//    @Bean
//    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
//        http
//                .csrf(ServerHttpSecurity.CsrfSpec::disable)
//                .authorizeExchange(exchange -> exchange
//                        .pathMatchers(
//                                "/swagger-ui.html",
//                                "/swagger-ui/**",
//                                "/v3/api-docs/**",
//                                "/order/v3/api-docs",
//                                "/webjars/swagger-ui/**",
//                                "/shop/v3/api-docs",
//                                "/payment/v3/api-docs",
//                                "/swagger-resources/**",
//                                "/webjars/**",
//                                "/favicon.ico"
//                        ).permitAll()
//                        .anyExchange().authenticated())
//                .oauth2Client(Customizer.withDefaults())
//                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
//                .oauth2ResourceServer(oauth2 -> oauth2
//                        .jwt(jwt -> jwt
//                                .jwtAuthenticationConverter(grantedAuthoritiesExtractor()) // Подключаем кастомный конвертер
//                        ))
//        ;
//
//        return http.build();
//    }
//
//    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
//        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
//        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new AuthServerRoleConverter());
//        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
//    }
//
//    @Bean
//    public CorsWebFilter corsFilter() {
//        CorsConfiguration config = new CorsConfiguration();
//        config.setAllowCredentials(true);
//        config.addAllowedOrigin("*");
//        config.addAllowedHeader("*");
//        config.addAllowedMethod("*");
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", config);
//
//        return new CorsWebFilter(source);
//    }
//}

@Configuration
@EnableWebFluxSecurity
public class ReactiveConfig {

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

    @Bean
    @Order(2)
    public SecurityWebFilterChain resourceServerChain(ServerHttpSecurity http) {
        // Все api-маршруты (order/shop/payment) требуют JWT: внутренние вызовы order-service
        // ходят напрямую через Eureka с собственным Authorization (пользовательский JWT или
        // сервисный токен client_credentials из ServiceTokenClient), поэтому маршрут /payment
        // больше не нужен фоновым задачам и закрыт для анонимных внешних клиентов.
        // Docs-маршруты (/order/v3/api-docs и т.п.) открыты — это метаданные swagger-ui за oauth2Login.
        http
                .securityMatcher(pathMatchers(
                        "/order/api/v1/**",
                        "/shop/api/v1/**",
                        "/payment/api/v1/**"
                ))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt
                                .jwtAuthenticationConverter(grantedAuthoritiesExtractor())
                        )
                );
        return http.build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new AuthServerRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

}
