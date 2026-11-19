package com.utochkin.notificationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.notificationservice.dto.AddressDto;
import com.utochkin.notificationservice.dto.OrderDtoForKafka;
import com.utochkin.notificationservice.dto.UserDto;
import com.utochkin.notificationservice.dto.OrderRequest;
import com.utochkin.notificationservice.dto.Status;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.math.BigDecimal;


@SpringBootTest(
        classes = NotificationServiceApplication.class,
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
@EmbeddedKafka(partitions = 1, topics = "topic-orders")
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, OrderDtoForKafka> kafkaTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;


    @BeforeEach
    void configureTemplate() {
        kafkaTemplate.setDefaultTopic("topic-orders");

        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
    }

    @Test
    @DisplayName("Публикация OrderDtoForKafka должен вызвать отправку по электронной почте")
    void kafkaPublish_triggersEmailService() throws Exception {
        OrderDtoForKafka order = new OrderDtoForKafka(
                UUID.randomUUID(),
                BigDecimal.valueOf(99.9),
                Status.SUCCESS,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now(),
                new AddressDto("Moscow", "Lenina", 1, 10),
                new UserDto("alice", "Alice", "Smith", "alice@example.com"),
                List.of(new OrderRequest(UUID.randomUUID(), 1)),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        kafkaTemplate.sendDefault(order);
        kafkaTemplate.flush();

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(mailSender).send(argThat((SimpleMailMessage msg) ->
                    Objects.requireNonNull(msg.getTo())[0].equals("alice@example.com")
                            && Objects.requireNonNull(msg.getSubject()).contains(order.getOrderUuid().toString())
                            && Objects.requireNonNull(msg.getText()).contains("Ваш заказ № " + order.getOrderUuid())
            ));
        });
    }

    @Test
    @DisplayName("Повторная публикация события (tryAcquire=false) не отправляет письмо повторно")
    void kafkaPublish_duplicateEvent_skipsEmail() throws Exception {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);

        OrderDtoForKafka order = new OrderDtoForKafka(
                UUID.randomUUID(),
                BigDecimal.valueOf(99.9),
                Status.SUCCESS,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now(),
                new AddressDto("Moscow", "Lenina", 1, 10),
                new UserDto("alice", "Alice", "Smith", "alice@example.com"),
                List.of(new OrderRequest(UUID.randomUUID(), 1)),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        kafkaTemplate.sendDefault(order);
        kafkaTemplate.flush();

        Awaitility.await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4)).until(() -> true);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

}
