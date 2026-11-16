package com.utochkin.historyservice.services;

import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.models.ProcessedEvent;
import com.utochkin.historyservice.repositories.OrderHistoryRepository;
import com.utochkin.historyservice.repositories.ProcessedEventRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@AllArgsConstructor
@Log4j2
public class OrderHistoryService {

    private final OrderHistoryRepository orderHistoryRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final MongoTemplate mongoTemplate;

    @KafkaListener(topics = "topic-orders", groupId = "history-group")
    public void consume(OrderDtoForKafka orderDto, Acknowledgment ack) {
        log.info("OrderHistoryService: получено сообщение из Kafka: {}", orderDto);

        if (orderDto.getEventId() == null) {
            log.warn("OrderHistoryService: событие без eventId для заказа {} — обрабатываем без дедупликации", orderDto.getOrderUuid());
            appendToHistory(orderDto);
            ack.acknowledge();
            return;
        }

        try {
            processedEventRepository.insert(new ProcessedEvent(orderDto.getEventId().toString(), LocalDateTime.now()));
        } catch (DuplicateKeyException e) {
            log.info("OrderHistoryService: событие {} для заказа {} уже обработано — пропускаем (дедупликация)",
                    orderDto.getEventId(), orderDto.getOrderUuid());
            ack.acknowledge();
            return;
        }

        try {
            appendToHistory(orderDto);
            ack.acknowledge();
        } catch (Exception e) {
            processedEventRepository.deleteById(orderDto.getEventId().toString());
            log.warn("OrderHistoryService: не удалось сохранить историю для события {} — маркер обработанности удалён для повторной обработки",
                    orderDto.getEventId());
            throw e;
        }
    }

    private void appendToHistory(OrderDtoForKafka orderDto) {
        String username = orderDto.getUserDto().username();
        mongoTemplate.upsert(
                Query.query(Criteria.where("username").is(username)),
                new Update().push("orders", orderDto),
                OrderHistory.class);
        log.info("OrderHistoryService: сохранена история заказа для пользователя {}", username);
    }
}
