package com.utochkin.deliveryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * delivery-service: доставка оплаченных заказов курьерами.
 * Вход: консьюмер topic-orders (SUCCESS — создать доставку, REFUNDED — отменить).
 * Scheduler'ы: назначение свободных курьеров, T+5мин → DELIVERING (Feign в order-service),
 * завершение доставки → DELIVERED; синхронизация справочника курьеров из Keycloak.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class DeliveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }
}
