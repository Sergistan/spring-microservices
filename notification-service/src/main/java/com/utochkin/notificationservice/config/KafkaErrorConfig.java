package com.utochkin.notificationservice.config;

import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Log4j2
public class KafkaErrorConfig {

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(Environment environment,
                                                 ObjectProvider<KafkaConnectionDetails> connectionDetails) {
        // В тестах на Testcontainers адрес брокера приходит через KafkaConnectionDetails (@ServiceConnection),
        // а не через свойство spring.kafka.bootstrap-servers — учитываем оба источника
        KafkaConnectionDetails kafkaConnectionDetails = connectionDetails.getIfAvailable();
        String bootstrapServers = kafkaConnectionDetails != null
                ? String.join(",", kafkaConnectionDetails.getProducerBootstrapServers())
                : environment.getProperty("spring.kafka.bootstrap-servers");
        if (bootstrapServers == null) {
            throw new IllegalStateException(
                    "KafkaErrorConfig: не задан spring.kafka.bootstrap-servers и нет KafkaConnectionDetails — DLQ-продюсер создать нельзя");
        }

        Map<String, Object> rawProps = new HashMap<>();
        rawProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        rawProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        rawProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        KafkaOperations<byte[], byte[]> rawTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(rawProps));

        Map<String, Object> jsonProps = new HashMap<>();
        jsonProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        jsonProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        jsonProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        jsonProps.put("spring.json.use.type.headers", false);
        KafkaOperations<String, Object> jsonTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(jsonProps));

        Map<Class<?>, KafkaOperations<?, ?>> templates = Map.of(byte[].class, rawTemplate, Object.class, jsonTemplate);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates,
                (record, ex) -> {
                    log.error("KafkaErrorConfig: запись из {} не обработана после всех попыток ({}: {}) — переношу в {}{}",
                            record.topic(), ex.getClass().getSimpleName(), ex.getMessage(), record.topic(), DLT_SUFFIX);
                    return new TopicPartition(record.topic() + DLT_SUFFIX, record.partition());
                });

        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
    }
}
