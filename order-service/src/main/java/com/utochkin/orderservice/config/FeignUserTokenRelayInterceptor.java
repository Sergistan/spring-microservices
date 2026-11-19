package com.utochkin.orderservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Подстановка Authorization во ВСЕ исходящие Feign-вызовы order-service:
 * веб-поток — ретранслируется JWT пользователя из SecurityContext (вызовы «от имени» пользователя);
 * фоновый поток (SecurityContext пуст) — подставляется сервисный токен из ServiceTokenClient;
 * если и он недоступен — вызов уходит без Authorization и получит 401 (обработает resilience4j).
 */
@Component
@Log4j2
public class FeignUserTokenRelayInterceptor implements RequestInterceptor {

    private final ServiceTokenClient serviceTokenClient;

    public FeignUserTokenRelayInterceptor(ServiceTokenClient serviceTokenClient) {
        this.serviceTokenClient = serviceTokenClient;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        // 1) Пользовательский JWT из контекста запроса (веб-поток: createOrder/pay/refund)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthToken) {
            requestTemplate.header("Authorization", "Bearer " + jwtAuthToken.getToken().getTokenValue());
            return;
        }

        // 2) Фоновый поток (ReconciliationJob и др.): SecurityContext пуст — берём сервисный токен
        try {
            requestTemplate.header("Authorization", "Bearer " + serviceTokenClient.getServiceToken());
            log.debug("FeignUserTokenRelayInterceptor: фоновый вызов {} — подставлен сервисный токен",
                    requestTemplate.path());
        } catch (IllegalStateException e) {
            log.warn("FeignUserTokenRelayInterceptor: сервисный токен недоступен ({}), вызов {} уйдёт без Authorization",
                    e.getMessage(), requestTemplate.path());
        }
    }
}
