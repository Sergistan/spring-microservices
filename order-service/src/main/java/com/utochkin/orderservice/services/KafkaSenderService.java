package com.utochkin.orderservice.services;

import com.utochkin.orderservice.dto.OrderDtoForKafka;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class KafkaSenderService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(OrderDtoForKafka orderDtoForKafka) {

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send("topic-orders", orderDtoForKafka);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("Сообщение отправлено: " + orderDtoForKafka);
            } else {
                System.err.println("Ошибка при отправке сообщения: " + ex.getMessage());
            }
        });
    }
}
