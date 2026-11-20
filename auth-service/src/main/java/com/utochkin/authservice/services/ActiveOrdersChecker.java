package com.utochkin.authservice.services;

import com.utochkin.authservice.feign.OrderServiceClient;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Обёртка Feign-вызова order-service с retry (инстанс retryHasActiveOrders в config-repo).
 * Вынесена в отдельный бин сознательно: @Retry работает через Spring-прокси, поэтому
 * вызов должен идти через публичный метод ДРУГОГО бина (self-invocation обошёл бы аспект).
 */
@Component
@RequiredArgsConstructor
public class ActiveOrdersChecker {

    private final OrderServiceClient orderServiceClient;

    /** true — у пользователя есть заказы не в финальных статусах (включая стадии доставки). */
    @Retry(name = "retryHasActiveOrders")
    public Boolean hasActiveOrders(String subId) {
        return orderServiceClient.hasActiveOrders(subId);
    }
}
