package com.utochkin.shopservice.config;

import com.utochkin.shopservice.exceptions.CustomAccessDeniedHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security-конфигурация shop-service: JWT (Keycloak) для всех /shop/api/v1/**, при этом
 * изменение каталога (addProduct/deleteProduct/updateProduct) — только роль ADMIN
 * (realm-роль из JWT конвертирует AuthServerRoleConverter). Чтение каталога и
 * Feign-эндпоинты для order-service (checkOrder/остатки) — любому аутентифицированному сервису.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class Config {

    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    /**
     * Цепочка: stateless JWT; ADMIN-only на изменение каталога, остальное — любой
     * аутентифицированный; отказ по роли уходит в CustomAccessDeniedHandler (понятный JSON).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new AuthServerRoleConverter());

        http
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception ->
                        exception.accessDeniedHandler(customAccessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**", "/favicon.ico", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/shop/api/v1/addProduct/**").hasRole("ADMIN")
                        .requestMatchers("/shop/api/v1/deleteProduct/**").hasRole("ADMIN")
                        .requestMatchers("/shop/api/v1/updateProduct/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        return http.build();
    }
}

