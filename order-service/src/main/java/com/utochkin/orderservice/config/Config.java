package com.utochkin.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security-конфигурация order-service: все /order/api/v1/** требуют JWT (Keycloak, issuer из config-repo),
 * публично открыты только swagger-docs и health/prometheus. Проверенный JWT попадает в SecurityContext —
 * оттуда его ретранслирует в исходящие Feign-вызовы FeignUserTokenRelayInterceptor.
 */
@Configuration
@EnableWebSecurity
public class Config {
    /**
     * Цепочка фильтров: stateless (без сессий), все запросы — с валидным JWT,
     * публично только swagger/health/prometheus. Валидный токен кладёт
     * JwtAuthenticationToken в SecurityContext (оттуда его забирает FeignUserTokenRelayInterceptor).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**", "/favicon.ico", "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
