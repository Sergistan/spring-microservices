package com.utochkin.orderservice.services;

import com.utochkin.orderservice.dto.OrderDtoForKafka;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class KafkaSenderService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(OrderDtoForKafka orderDtoForKafka) {
        log.info("KafkaSenderService: отправка в topic-orders: {}", orderDtoForKafka);

        kafkaTemplate.send("topic-orders", orderDtoForKafka)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("KafkaSenderService: сообщение успешно отправлено, offset={}", result.getRecordMetadata().offset());
                    } else {
                        log.error("KafkaSenderService: ошибка при отправке: {}", ex.getMessage(), ex);
                    }
                });
    }
}
