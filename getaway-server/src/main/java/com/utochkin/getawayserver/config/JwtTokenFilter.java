package com.utochkin.getawayserver.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

//@Component
//@Slf4j
//public class JwtTokenFilter implements GlobalFilter {
//
//    @Value("${jwt.public-key}")
//    private String publicKey;
//
//    @SneakyThrows
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
//        if (authHeader != null && authHeader.startsWith("Bearer ")) {
//            String token = authHeader.substring(7);
//            Claims claims = parseJwtToken(token); // Распарсить токен
//
//            // Извлекаем роли
//            Map<String, Object> resourceAccess = claims.get("resource_access", Map.class);
//            Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get("spring-microservices");
//            List<String> roles = (List<String>) clientAccess.get("roles");
//
//            String role = roles != null && !roles.isEmpty() ? roles.get(0) : "UNKNOWN";
//
//            exchange.getRequest().mutate()
//                    .header("X-User-SubId", claims.get("sub").toString()) // ID пользователя
//                    .header("X-User-UserName", claims.get("preferred_username").toString()) // Username пользователя
//                    .header("X-User-FirstName", URLEncoder.encode(claims.get("given_name").toString(), StandardCharsets.UTF_8)) // Имя пользователя
//                    .header("X-User-LastName", URLEncoder.encode(claims.get("family_name").toString(), StandardCharsets.UTF_8)) // Фамилия пользователя
//                    .header("X-User-Email", claims.get("email").toString()) // Email пользователя
//                    .header("X-User-Role", role) // Роль пользователя
//                    .build();
//        }
//        return chain.filter(exchange);
//    }
//
//    private Claims parseJwtToken(String token) throws Exception {
//        PublicKey key = readRsaPublicKey(publicKey);
//        return Jwts.parserBuilder()
//                .setSigningKey(key)
//                .build()
//                .parseClaimsJws(token)
//                .getBody();
//    }
//
//    private static PublicKey readRsaPublicKey(String pemEncodedKey) throws Exception {
//        // Удаляем заголовок и окончание, если они есть
//        String publicKeyPEM = pemEncodedKey
//                .replace("-----BEGIN PUBLIC KEY-----", "")
//                .replace("-----END PUBLIC KEY-----", "")
//                .replaceAll("\\s+", "");
//
//        byte[] decodedKey = Base64.getDecoder().decode(publicKeyPEM);
//        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
//
//        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//        return keyFactory.generatePublic(keySpec);
//    }
//}


@Component
@Slf4j
public class JwtTokenFilter implements GlobalFilter, Ordered {

    @Value("${jwt.public-key}")
    private String publicKey;

    @Override
    public int getOrder() {
        // чтобы этот фильтр шёл перед TokenRelay, но после ранних security‑фильтров
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @SneakyThrows
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Claims claims = parseJwtToken(token);

            // Собираем мутированный запрос
            ServerHttpRequest mutatedReq = exchange.getRequest()
                    .mutate()
                    .header("X-User-SubId", claims.get("sub").toString())
                    .header("X-User-UserName", claims.get("preferred_username").toString())
                    .header("X-User-FirstName", URLEncoder.encode(claims.get("given_name").toString(), StandardCharsets.UTF_8))
                    .header("X-User-LastName", URLEncoder.encode(claims.get("family_name").toString(), StandardCharsets.UTF_8))
                    .header("X-User-Email", claims.get("email").toString())
                    .header("X-User-Role", extractRole(claims))
                    .build();

            // Создаём новый exchange с этим запросом
            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedReq)
                    .build();

            // И дальше пускаем мутированный exchange
            return chain.filter(mutatedExchange);
        }

        // если нет авторизации — пускаем оригинал
        return chain.filter(exchange);
    }

    private String extractRole(Claims claims) {
        Map<String, Object> resourceAccess = claims.get("resource_access", Map.class);
        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get("spring-microservices");
        List<String> roles = (List<String>) clientAccess.get("roles");
        return (roles != null && !roles.isEmpty())
                ? roles.get(0)
                : "UNKNOWN";
    }


        private Claims parseJwtToken(String token) throws Exception {
        PublicKey key = readRsaPublicKey(publicKey);
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private static PublicKey readRsaPublicKey(String pemEncodedKey) throws Exception {
        // Удаляем заголовок и окончание, если они есть
        String publicKeyPEM = pemEncodedKey
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] decodedKey = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

}