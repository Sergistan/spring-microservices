package com.utochkin.authservice.config;

import com.utochkin.authservice.keycloak.ServiceTokenClient;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Ко всем исходящим Feign-вызовам добавляет сервисный токен auth-service-client
 * (client_credentials): у auth-service нет пользовательских запросов к order-service,
 * поэтому relay пользовательского JWT (как в order-service) не нужен — всегда сервисный.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class ServiceTokenRequestInterceptor implements RequestInterceptor {

    private final ServiceTokenClient serviceTokenClient;

    @Override
    public void apply(RequestTemplate template) {
        try {
            template.header("Authorization", "Bearer " + serviceTokenClient.getServiceToken());
        } catch (IllegalStateException e) {
            // Токен недоступен (нет регистрации/Keycloak молчит) — вызов уйдёт без Authorization
            // и получит понятный 403 от azp-гварда order-service; логируем причину.
            log.warn("ServiceTokenRequestInterceptor: сервисный токен недоступен ({}), вызов уйдёт без Authorization",
                    e.getMessage());
        }
    }
}
