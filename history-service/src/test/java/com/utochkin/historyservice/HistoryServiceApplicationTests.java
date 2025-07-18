package com.utochkin.historyservice;

import com.utochkin.historyservice.dto.AddressDto;
import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.dto.UserDto;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.models.OrderRequest;
import com.utochkin.historyservice.models.Status;
import com.utochkin.historyservice.repositories.OrderHistoryRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = HistoryServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.data.mongodb.database=testdb",
                "spring.mongodb.embedded.version=4.0.2",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.kafka.listener.missing-topics-fatal=false",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.compatibility-verifier.enabled=false",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
                "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=*"
        }
)
@EmbeddedKafka(partitions = 1, topics = "topic-orders")
@ActiveProfiles("test")
public class HistoryServiceApplicationTests {

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
                500.0,
                Status.SUCCESS,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now(),
                new AddressDto("SPb", "Nevsky", 20, 5),
                new UserDto("alice", "Alice", "White", "alice@example.com"),
                List.of(new OrderRequest(UUID.randomUUID(), 3)),
                UUID.randomUUID()
        );

        kafkaTemplate.sendDefault(orderDto);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<OrderHistory> historyOpt = orderHistoryRepository.findByUsername("alice");
            assertThat(historyOpt).isPresent();
            assertThat(historyOpt.get().getOrders()).hasSize(1);
            assertThat(historyOpt.get().getOrders().getFirst().getOrderUuid()).isEqualTo(orderDto.getOrderUuid());
        });
    }
}
