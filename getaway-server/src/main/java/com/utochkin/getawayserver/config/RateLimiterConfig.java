package com.utochkin.getawayserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Rate limiting gateway (token bucket в Redis, фильтр RequestRateLimiter включён в маршрутах
 * order/shop/payment в config-repo). KeyResolver: ключ = "user:<sub>" для авторизованных
 * (общий лимит на пользователя для всех реплик gateway), fallback "ip:<адрес>" для анонимов.
 * Превышение -> 429; при недоступном Redis фильтр отвечает 500 (fail-closed).
 */
@Configuration
public class RateLimiterConfig {

    /** Ключ бакета: "user:<sub>" для JWT-принципала, "ip:<адрес>" для анонимов. */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(principal -> "user:" + principal.getToken().getSubject())
                .switchIfEmpty(Mono.fromCallable(() -> "ip:" + resolveRemoteAddress(exchange)));
    }

    /** IP клиента с защитой от null (экзотические запросы -> "unknown"). */
    private String resolveRemoteAddress(org.springframework.web.server.ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }
}
