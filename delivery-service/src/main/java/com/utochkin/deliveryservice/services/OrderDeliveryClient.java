package com.utochkin.deliveryservice.services;

import com.utochkin.deliveryservice.feign.OrderServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Обёртка Feign-вызовов order-service с retry/circuit breaker (инстансы
 * circuitBreakerOrderDelivery / retryOrderDelivery в config-repo). Вынесена в отдельный бин
 * сознательно: @Scheduled вызывает метод джобы в обход AOP-прокси, поэтому аннотации
 * resilience4j должны стоять на методе ДРУГОГО бина, вызываемого через прокси.
 * FeignException.Conflict (409 — возврат успел раньше) не входит в retryExceptions/recordExceptions:
 * не ретраится и не размыкает цепь, его ловит DeliveryProgressJob.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class OrderDeliveryClient {

    private final OrderServiceClient orderServiceClient;

    /** Заказ → DELIVERING (CAS из SUCCESS на стороне order-service). */
    @CircuitBreaker(name = "circuitBreakerOrderDelivery", fallbackMethod = "startDeliveryFallback")
    @Retry(name = "retryOrderDelivery", fallbackMethod = "startDeliveryFallback")
    public void startDelivery(UUID orderUuid) {
        orderServiceClient.startDelivery(orderUuid);
    }

    /** Заказ → DELIVERED (CAS из DELIVERING). */
    @CircuitBreaker(name = "circuitBreakerOrderDelivery", fallbackMethod = "completeDeliveryFallback")
    @Retry(name = "retryOrderDelivery", fallbackMethod = "completeDeliveryFallback")
    public void completeDelivery(UUID orderUuid) {
        orderServiceClient.completeDelivery(orderUuid);
    }

    /** Fallback: пробрасываем исходную ошибку — джоба сама различает 409 (бизнес) и технический сбой. */
    public void startDeliveryFallback(UUID orderUuid, Throwable throwable) {
        throw (throwable instanceof RuntimeException re) ? re
                : new IllegalStateException("startDelivery не выполнен: " + throwable.getMessage(), throwable);
    }

    public void completeDeliveryFallback(UUID orderUuid, Throwable throwable) {
        throw (throwable instanceof RuntimeException re) ? re
                : new IllegalStateException("completeDelivery не выполнен: " + throwable.getMessage(), throwable);
    }
}
