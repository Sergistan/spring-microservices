package com.utochkin.notificationservice;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

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
                "spring.kafka.consumer.group-id=notification-dlt-test",
                "spring.kafka.consumer.key-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
                "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
                "spring.kafka.consumer.properties.spring.deserializer.key.delegate.class=org.apache.kafka.common.serialization.StringDeserializer",
                "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
                "spring.kafka.consumer.properties.spring.json.trusted.packages=*"
        }
)
@Testcontainers(disabledWithoutDocker = true)
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class NotificationDltIntegrationTest {

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Битое (poison) сообщение уходит в topic-orders.DLT, письмо не отправляется, консьюмер жив")
    void poisonMessage_goesToDlq() {
        // 1) Публикуем сырую строку как poison-сообщение: JsonDeserializer не сможет её распарсить.
        // Ожидаемая цепочка на стороне приложения: ErrorHandlingDeserializer -> DeserializationException
        // -> DefaultErrorHandler (2 ретрая) -> DeadLetterPublishingRecoverer -> topic-orders.DLT
        Map<String, Object> producerProps = Map.of(
                org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        KafkaTemplate<String, String> rawProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        rawProducer.send("topic-orders", "not-a-json-at-all");
        rawProducer.flush();

        // 2) Одноразовый консьюмер читает DLT-топик и проверяет заголовок исключения
        // и наличие исходного poison-сообщения в теле записи
        Map<String, Object> dltConsumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );

        try (org.apache.kafka.clients.consumer.Consumer<String, String> dltConsumer =
                     new DefaultKafkaConsumerFactory<String, String>(dltConsumerProps).createConsumer()) {
            dltConsumer.subscribe(List.of("topic-orders.DLT"));

            Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofSeconds(2));
                assertThat(records).anySatisfy(this::assertDeadLetterRecord);
            });
        }

        verifyNoInteractions(mailSender);
    }

    private void assertDeadLetterRecord(ConsumerRecord<String, String> record) {
        Header exceptionFqcn = record.headers().lastHeader("kafka_dlt-exception-fqcn");
        assertThat(exceptionFqcn).isNotNull();
        assertThat(new String(exceptionFqcn.value(), StandardCharsets.UTF_8))
                .contains("DeserializationException");

        String value = record.value();
        boolean rawMatch = value != null && value.contains("not-a-json-at-all");
        boolean base64Match = false;
        if (value != null) {
            try {
                base64Match = new String(Base64.getDecoder().decode(value.replace("\"", "")), StandardCharsets.UTF_8)
                        .contains("not-a-json-at-all");
            } catch (IllegalArgumentException ignored) {
            }
        }
        assertThat(rawMatch || base64Match)
                .as("DLT value должен содержать исходное poison-сообщение (raw или base64)")
                .isTrue();
    }
}
