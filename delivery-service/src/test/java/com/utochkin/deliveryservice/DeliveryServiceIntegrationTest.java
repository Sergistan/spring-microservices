package com.utochkin.deliveryservice;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.utochkin.deliveryservice.dto.OrderDtoForKafka;
import com.utochkin.deliveryservice.feign.OrderServiceClient;
import com.utochkin.deliveryservice.keycloak.KeycloakAdminClient;
import com.utochkin.deliveryservice.models.Delivery;
import com.utochkin.deliveryservice.models.DeliveryStatus;
import com.utochkin.deliveryservice.repositories.DeliveryRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест на реальных Kafka + PostgreSQL (Testcontainers, @ServiceConnection):
 * событие SUCCESS из topic-orders (с заголовком __TypeId__ продюсера order-service и
 * датами формата yyyy-MM-dd HH:mm) создаёт доставку; повтор того же eventId — без дубля;
 * последующий REFUNDED отменяет доставку. Feign/Keycloak/JWT — моки.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration," +
                "org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientConfiguration," +
                "io.github.resilience4j.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration," +
                "io.github.resilience4j.retry.autoconfigure.RetryAutoConfiguration," +
                "org.springframework.cloud.openfeign.FeignAutoConfiguration",
        "eureka.client.enabled=false",
        // consumer-контракт (в prod приходит из config-repo): ErrorHandlingDeserializer + JsonDeserializer
        // с type mapping: FQCN продюсера order-service -> наш класс
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
        "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
        "spring.kafka.consumer.properties.spring.json.type.mapping=com.utochkin.orderservice.dto.OrderDtoForKafka:com.utochkin.deliveryservice.dto.OrderDtoForKafka",
        "spring.kafka.listener.ack-mode=MANUAL_IMMEDIATE",
        "spring.kafka.listener.missing-topics-fatal=false",
        // путь к changelog в prod приходит из config-repo; в тесте задаём явно — миграции выполняются на реальном Postgres
        "spring.liquibase.change-log=classpath:changelog/db.changelog-master.yaml",
        // джобы отключаем: стадии двигает сам тест напрямую через DeliveryService
        "delivery.progress.fixed-delay=3600000",
        "delivery.courier-sync.fixed-delay=3600000",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Testcontainers(disabledWithoutDocker = true)
class DeliveryServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final com.fasterxml.jackson.databind.ObjectMapper jsonMapper =
            JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private com.utochkin.deliveryservice.services.DeliveryService deliveryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private OrderServiceClient orderServiceClient;

    @MockitoBean
    private KeycloakAdminClient keycloakAdminClient;

    @Test
    @DisplayName("SUCCESS-событие создаёт доставку; дубль eventId игнорируется; REFUNDED отменяет")
    void successCreatesDelivery_duplicateIgnored_refundCancels() throws Exception {
        UUID orderUuid = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        send(orderUuid, eventId, "SUCCESS");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Delivery> delivery = deliveryRepository.findByOrderUuid(orderUuid);
            assertThat(delivery).isPresent();
            assertThat(delivery.get().getStatus()).isEqualTo(DeliveryStatus.SEARCHING_COURIER);
        });

        // Дубль события (at-least-once): та же доставка, без изменений
        send(orderUuid, eventId, "SUCCESS");
        Awaitility.await().during(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(deliveryRepository.count()).isEqualTo(1));

        // Возврат в 5-минутном окне — доставка отменяется
        send(orderUuid, UUID.randomUUID(), "REFUNDED");
        Awaitility.await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Delivery> delivery = deliveryRepository.findByOrderUuid(orderUuid);
            assertThat(delivery).isPresent();
            assertThat(delivery.get().getStatus()).isEqualTo(DeliveryStatus.CANCELLED);
        });
    }

    /** Отправка события «как это делает order-service»: JSON-строка (StringSerializer) + ключ orderUuid + __TypeId__ продюсера. */
    private void send(UUID orderUuid, UUID eventId, String status) throws Exception {
        OrderDtoForKafka dto = new OrderDtoForKafka();
        dto.setOrderUuid(orderUuid);
        dto.setOrderStatus(com.utochkin.deliveryservice.dto.Status.valueOf(status));
        dto.setEventId(eventId);
        dto.setCreatedAt(LocalDateTime.now().withNano(0));
        dto.setUpdatedAt(LocalDateTime.now().withNano(0));

        String payload = jsonMapper.writeValueAsString(dto);
        kafkaTemplate.send(MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, "topic-orders")
                .setHeader(KafkaHeaders.KEY, orderUuid.toString())
                .setHeader("__TypeId__", "com.utochkin.orderservice.dto.OrderDtoForKafka")
                .build());
    }
}
