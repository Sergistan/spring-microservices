package com.utochkin.historyservice.services;

import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.models.ProcessedEvent;
import com.utochkin.historyservice.repositories.OrderHistoryRepository;
import com.utochkin.historyservice.repositories.ProcessedEventRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
@AllArgsConstructor
@Log4j2
public class OrderHistoryService {

    private final OrderHistoryRepository orderHistoryRepository;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "topic-orders", groupId = "history-group")
    public void consume(OrderDtoForKafka orderDto) {
        log.info("OrderHistoryService: получено сообщение из Kafka: {}", orderDto);

        if (orderDto.getEventId() == null) {
            log.warn("OrderHistoryService: событие без eventId для заказа {} — обрабатываем без дедупликации", orderDto.getOrderUuid());
            appendToHistory(orderDto);
            return;
        }

        try {
            processedEventRepository.insert(new ProcessedEvent(orderDto.getEventId().toString(), LocalDateTime.now()));
        } catch (DuplicateKeyException e) {
            log.info("OrderHistoryService: событие {} для заказа {} уже обработано — пропускаем (дедупликация)",
                    orderDto.getEventId(), orderDto.getOrderUuid());
            return;
        }

        try {
            appendToHistory(orderDto);
        } catch (Exception e) {
            processedEventRepository.deleteById(orderDto.getEventId().toString());
            log.warn("OrderHistoryService: не удалось сохранить историю для события {} — маркер обработанности удалён для повторной обработки",
                    orderDto.getEventId());
            throw e;
        }
    }

    private void appendToHistory(OrderDtoForKafka orderDto) {
        OrderHistory orderHistory = orderHistoryRepository
                .findByUsername(orderDto.getUserDto().username())
                .orElseGet(() -> {
                    log.debug("OrderHistoryService: создаём новый OrderHistory для пользователя {}", orderDto.getUserDto().username());
                    return new OrderHistory(orderDto.getUserDto().username(), new ArrayList<>());
                });

        orderHistory.getOrders().add(orderDto);
        orderHistoryRepository.save(orderHistory);
        log.info("OrderHistoryService: сохранена история заказа для пользователя {}", orderDto.getUserDto().username());
    }
}
