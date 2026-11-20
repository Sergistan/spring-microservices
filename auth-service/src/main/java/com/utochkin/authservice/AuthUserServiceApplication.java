package com.utochkin.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * auth-service: саморегистрация пользователей (покупатель USER / курьер COURIER) и удаление
 * собственного аккаунта. Источник истины о пользователях — Keycloak (Admin REST API);
 * своей БД у сервиса нет. Перед удалением аккаунта проверяет активные заказы в order-service.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class AuthUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthUserServiceApplication.class, args);
    }
}
