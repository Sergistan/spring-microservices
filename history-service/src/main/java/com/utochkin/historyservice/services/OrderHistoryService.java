package com.utochkin.historyservice.services;

import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.repositories.OrderHistoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@AllArgsConstructor
public class OrderHistoryService {

    private final OrderHistoryRepository orderHistoryRepository;

    @KafkaListener(topics = "topic-orders", groupId = "history-group")
    public void consume(OrderDtoForKafka orderDto) {
        System.out.println("Получено сообщение из Kafka: " + orderDto);
        OrderHistory orderHistory = orderHistoryRepository.findByUsername(orderDto.getUserDto().username())
                .orElseGet(() -> new OrderHistory(orderDto.getUserDto().username(), new ArrayList<>()));

        orderHistory.getOrders().add(orderDto);

        orderHistoryRepository.save(orderHistory);
    }
}
