package com.utochkin.orderservice.resilience;

import com.utochkin.orderservice.controllers.ShopController;
import com.utochkin.orderservice.exceptions.ServiceUnavailableException;
import com.utochkin.orderservice.request.OrderRequest;
import com.utochkin.orderservice.services.OrderService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@SpringBootTest(properties = {
        "spring.cloud.compatibility-verifier.enabled=false",
        "spring.liquibase.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "resilience4j.circuitbreaker.instances.circuitBreakerCheckOrder.slidingWindowSize=5",
        "resilience4j.circuitbreaker.instances.circuitBreakerCheckOrder.permittedNumberOfCallsInHalfOpenState=3",
        "resilience4j.circuitbreaker.instances.circuitBreakerCheckOrder.waitDurationInOpenState=10s",
        "resilience4j.circuitbreaker.instances.circuitBreakerCheckOrder.failureRateThreshold=50",
        "resilience4j.retry.instances.retryCheckOrder.max-attempts=3",
        "resilience4j.retry.instances.retryCheckOrder.waitDuration=10ms"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ResilienceTest {

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private ShopController shopController;

    private List<OrderRequest> dtos;

    @Autowired
    private CircuitBreakerRegistry cbRegistry;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void init() {
        dtos = List.of(new OrderRequest(UUID.randomUUID(), 5));
    }

    @AfterEach
    void resetRegistry() {
        cbRegistry.circuitBreaker("circuitBreakerCheckOrder").reset();
    }

    @Test
    @DisplayName("Retry: 1-я попытка упала → 2-я успешна, CircuitBreaker остаётся CLOSED")
    void retrySucceedsAfterOneFailure() {
        // 1‑й вызов кидает, 2‑й возвращает true
        given(shopController.checkOrder(dtos)).willThrow(new RuntimeException("remote error")).willReturn(true);

        boolean ok = orderService.checkOrder(dtos);
        assertThat(ok).isTrue();

        // убедимся, что было именно 2 вызова: initial + 1 retry
        then(shopController).should(times(2)).checkOrder(dtos);

        CircuitBreaker cb = cbRegistry.circuitBreaker("circuitBreakerCheckOrder");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("Retry: все 3 попытки падают → fallback бросает ServiceUnavailableException, CircuitBreaker остаётся CLOSED")
    void retryAllFailuresThenFallback() {
        // shopController всегда кидает
        given(shopController.checkOrder(dtos)).willThrow(new RuntimeException("remote error"));

        // проверяем, что по трём попыткам уйдёт в fallback
        assertThatThrownBy(() -> orderService.checkOrder(dtos))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("временно недоступен");

        // Retry сделал initial + 2 retry = 3 вызова
        then(shopController).should(times(3)).checkOrder(dtos);

        // CircuitBreaker ещё не достиг порога slidingWindowSize=5, поэтому остаётся CLOSED
        CircuitBreaker cb = cbRegistry.circuitBreaker("circuitBreakerCheckOrder");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("CircuitBreaker открывается при 3 из 5 отказов (60% >50%)")
    void circuitBreakerOpensWhenFailureRateExceeded() {
        // настроим shopController: первые 3 вызова — ошибки, остальные (до 5) — уже не будут вызваны
        given(shopController.checkOrder(dtos))
                .willThrow(new RuntimeException("err1"))
                .willThrow(new RuntimeException("err2"))
                .willThrow(new RuntimeException("err3"));

        // выполняем 3 попытки
        IntStream.range(0, 3).forEach(i -> {
            try {
                orderService.checkOrder(dtos);
            } catch (ServiceUnavailableException ignored) {
            }
        });

        // CircuitBreaker оценил: 3 failures из 3 вызовов (3 < slidingWindowSize=5, но failureRate 100%>50%)
        CircuitBreaker cb = cbRegistry.circuitBreaker("circuitBreakerCheckOrder");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("CircuitBreaker остаётся CLOSED если отказов меньше 50% из 5")
    void circuitBreakerRemainsClosedBelowThreshold() {
        // пусть из пяти вызовов 2 будут ошибками, 3 — успешны
        given(shopController.checkOrder(dtos))
                .willThrow(new RuntimeException("err1"))
                .willThrow(new RuntimeException("err2"))
                .willReturn(true)   // 3,4,5 вызовы
                .willReturn(true)
                .willReturn(true);

        // вызываем 5 раз подряд
        IntStream.range(0, 5).forEach(i -> {
            try {
                orderService.checkOrder(dtos);
            } catch (ServiceUnavailableException ignored) {
            }
        });

        // total 5 вызовов, 2 из них упало → 40% < 50%, CB остаётся CLOSED
        CircuitBreaker cb = cbRegistry.circuitBreaker("circuitBreakerCheckOrder");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
