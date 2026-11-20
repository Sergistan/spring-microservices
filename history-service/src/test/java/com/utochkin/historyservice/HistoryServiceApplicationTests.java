package com.utochkin.historyservice;

import com.utochkin.historyservice.dto.AddressDto;
import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.dto.UserDto;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.dto.OrderRequest;
import com.utochkin.historyservice.dto.Status;
import com.utochkin.historyservice.repositories.OrderHistoryRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;

@SpringBootTest(
        classes = HistoryServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.listener.missing-topics-fatal=false",
                "spring.kafka.listener.ack-mode=MANUAL_IMMEDIATE",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.compatibility-verifier.enabled=false",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
                "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=*"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public class HistoryServiceApplicationTests {

    // Реальные MongoDB и Kafka в контейнерах: публикуем событие в topic-orders,
    // слушатель приложения сохраняет историю в MongoDB (вместо встроенной flapdoodle-БД)
    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0.21");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    @Autowired
    private KafkaTemplate<String, OrderDtoForKafka> kafkaTemplate;

    @Autowired
    private OrderHistoryRepository orderHistoryRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setup() {
        orderHistoryRepository.deleteAll();
        kafkaTemplate.setDefaultTopic("topic-orders");
    }

    @Test
    @DisplayName("Отправка в Kafka сообщения и его сохранение в MongoDB")
    void kafkaMessage_shouldSaveToMongo() {
        OrderDtoForKafka orderDto = new OrderDtoForKafka(
                UUID.randomUUID(),
                BigDecimal.valueOf(500.0),
                Status.SUCCESS,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now(),
                new AddressDto("SPb", "Nevsky", 20, 5),
                new UserDto("alice", "Alice", "White", "alice@example.com"),
                List.of(new OrderRequest(UUID.randomUUID(), 3)),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null
        );

        kafkaTemplate.sendDefault(orderDto);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistory> historyOpt = orderHistoryRepository.findByUsername("alice");
            assertThat(historyOpt).isPresent();
            assertThat(historyOpt.get().getOrders()).hasSize(1);
            assertThat(historyOpt.get().getOrders().getFirst().getOrderUuid()).isEqualTo(orderDto.getOrderUuid());
        });
    }

    @Test
    @DisplayName("Повторная доставка того же события (один eventId) не создаёт дубль в истории")
    void duplicateEvent_shouldBeSkipped() {
        UUID eventId = UUID.randomUUID();
        OrderDtoForKafka orderDto = new OrderDtoForKafka(
                UUID.randomUUID(),
                BigDecimal.valueOf(500.0),
                Status.SUCCESS,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now(),
                new AddressDto("SPb", "Nevsky", 20, 5),
                new UserDto("bob", "Bob", "Grey", "bob@example.com"),
                List.of(new OrderRequest(UUID.randomUUID(), 3)),
                UUID.randomUUID(),
                eventId,
                null
        );

        kafkaTemplate.sendDefault(orderDto);
        kafkaTemplate.sendDefault(orderDto);
        kafkaTemplate.flush();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistory> historyOpt = orderHistoryRepository.findByUsername("bob");
            assertThat(historyOpt).isPresent();
            assertThat(historyOpt.get().getOrders()).hasSize(1);
            assertThat(historyOpt.get().getOrders().getFirst().getEventId()).isEqualTo(eventId);
        });
    }
}
