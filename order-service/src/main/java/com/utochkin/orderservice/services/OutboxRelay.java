package com.utochkin.orderservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.models.OutboxEvent;
import com.utochkin.orderservice.repositories.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Log4j2
public class OutboxRelay {

    public static final String TOPIC_ORDERS = "topic-orders";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay:1000}")
    public void pollOnce() {
        transactionTemplate.executeWithoutResult(status -> {
            List<OutboxEvent> batch = outboxRepository.lockBatch();
            if (batch.isEmpty()) {
                return;
            }
            log.info("OutboxRelay: к отправке {} событий из outbox (FOR UPDATE SKIP LOCKED)", batch.size());

            for (OutboxEvent event : batch) {
                try {
                    OrderDtoForKafka dto = objectMapper.readValue(event.getPayload(), OrderDtoForKafka.class);
                    kafkaTemplate.send(TOPIC_ORDERS, event.getOrderUuid().toString(), dto).get(10, TimeUnit.SECONDS);
                    outboxRepository.delete(event);
                    log.info("OutboxRelay: событие {} для заказа {} отправлено в {}", event.getEventId(), event.getOrderUuid(), TOPIC_ORDERS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("OutboxRelay: отправка прервана, событие {} останется в outbox и будет повторено", event.getEventId(), e);
                    return;
                } catch (Exception e) {
                    log.error("OutboxRelay: не удалось отправить событие {} — батч прерван, повтор будет выполнен на следующем цикле", event.getEventId(), e);
                    return;
                }
            }
        });
    }
}
