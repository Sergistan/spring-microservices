package com.utochkin.orderservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.models.OutboxEvent;
import com.utochkin.orderservice.repositories.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Log4j2
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void saveToOutbox(OrderDtoForKafka dto) {
        if (dto.getEventId() == null) {
            dto.setEventId(UUID.randomUUID());
        }
        try {
            String payload = objectMapper.writeValueAsString(dto);
            outboxRepository.save(OutboxEvent.builder()
                    .eventId(dto.getEventId())
                    .orderUuid(dto.getOrderUuid())
                    .payload(payload)
                    .createdAt(LocalDateTime.now())
                    .build());
            log.info("OutboxService: событие {} для заказа {} сохранено в outbox", dto.getEventId(), dto.getOrderUuid());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать событие для заказа " + dto.getOrderUuid(), e);
        }
    }
}
