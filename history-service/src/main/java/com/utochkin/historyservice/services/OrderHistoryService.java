package com.utochkin.historyservice.services;

import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.repositories.OrderHistoryRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@AllArgsConstructor
@Log4j2
public class OrderHistoryService {

    private final OrderHistoryRepository orderHistoryRepository;

    @KafkaListener(topics = "topic-orders", groupId = "history-group")
    public void consume(OrderDtoForKafka orderDto) {
        log.info("OrderHistoryService: получено сообщение из Kafka: {}", orderDto);

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
