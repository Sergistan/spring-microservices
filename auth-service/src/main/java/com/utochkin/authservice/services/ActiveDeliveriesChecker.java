package com.utochkin.authservice.services;

import com.utochkin.authservice.feign.DeliveryServiceClient;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Обёртка Feign-вызова delivery-service с retry (по образцу ActiveOrdersChecker):
 * отдельный бин, чтобы @Retry сработал через Spring-прокси (self-invocation обходит аспект).
 */
@Component
@RequiredArgsConstructor
public class ActiveDeliveriesChecker {

    private final DeliveryServiceClient deliveryServiceClient;

    /** Количество активных доставок курьера (0 — если курьера нет в справочнике). */
    @Retry(name = "retryHasActiveDeliveries")
    public Long activeDeliveriesCount(String subId) {
        return deliveryServiceClient.activeDeliveriesCount(subId);
    }
}
