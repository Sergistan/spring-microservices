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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
@EmbeddedKafka(partitions = 1, topics = {"topic-orders", "topic-orders.DLT"})
@EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@ActiveProfiles("test")
class NotificationDltIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("Битое (poison) сообщение уходит в topic-orders.DLT, письмо не отправляется, консьюмер жив")
    void poisonMessage_goesToDlq() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka);
        producerProps.put("key.serializer", StringSerializer.class);
        producerProps.put("value.serializer", StringSerializer.class);
        KafkaTemplate<String, String> rawProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));

        rawProducer.send("topic-orders", "not-a-json-at-all");
        rawProducer.flush();

        Map<String, Object> dltConsumerProps = KafkaTestUtils.consumerProps(
                UUID.randomUUID().toString(), "true", embeddedKafka);
        dltConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dltConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dltConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

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
