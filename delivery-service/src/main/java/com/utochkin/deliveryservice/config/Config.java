package com.utochkin.deliveryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security delivery-service: внутренний сервис — наружу через gateway НЕ выставляется,
 * все REST-пути требуют JWT (фоновые вызовы сами ходят с сервисным токеном); открыты
 * только actuator-эндпоинты для healthcheck/prometheus и swagger для отладки внутри сети.
 */
@Configuration
@EnableWebSecurity
public class Config {

    /**
     * Цепочка фильтров: stateless (без сессий), всё — с валидным JWT;
     * публично только swagger (внутренняя сеть) и actuator-эндпоинты (health/prometheus).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**",
                                "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
