package com.utochkin.shopservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class FeignUserTokenRelayInterceptor implements RequestInterceptor {


    @Override
    public void apply(RequestTemplate requestTemplate) {
        // Получаем текущую аутентификацию из SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuthToken = (JwtAuthenticationToken) authentication;
            String tokenValue = jwtAuthToken.getToken().getTokenValue();
            // Добавляем заголовок Authorization с переданным JWT
            requestTemplate.header("Authorization", "Bearer " + tokenValue);
        }
    }
}

