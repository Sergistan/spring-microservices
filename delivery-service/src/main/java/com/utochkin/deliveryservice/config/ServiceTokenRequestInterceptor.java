package com.utochkin.deliveryservice.config;

import com.utochkin.deliveryservice.keycloak.ServiceTokenClient;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

/**
 * Ко всем исходящим Feign-вызовам (order-service) добавляет сервисный токен
 * delivery-service-client (client_credentials): фоновые @Scheduled-потоки не имеют
 * пользовательского JWT, ретрансляция не нужна — всегда сервисный токен.
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
            log.warn("ServiceTokenRequestInterceptor: сервисный токен недоступен ({}), вызов уйдёт без Authorization",
                    e.getMessage());
        }
    }
}
