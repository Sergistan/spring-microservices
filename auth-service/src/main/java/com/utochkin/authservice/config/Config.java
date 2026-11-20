package com.utochkin.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security auth-service: анонимной должна быть ТОЛЬКО регистрация
 * (POST /auth/api/v1/users) и служебные разрешённые пути (swagger, actuator);
 * удаление аккаунта и всё остальное требуют JWT (гейтвей дополнительно
 * проставляет доверенный заголовок X-User-SubId из claim sub).
 */
@Configuration
@EnableWebSecurity
public class Config {

    /**
     * Цепочка фильтров: stateless (без сессий); публично открыты swagger/actuator и ЕДИНСТВЕННЫЙ
     * анонимный путь POST /auth/api/v1/users (саморегистрация) — удаление аккаунта и всё
     * остальное требуют валидный JWT.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**",
                                "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/api/v1/users").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
