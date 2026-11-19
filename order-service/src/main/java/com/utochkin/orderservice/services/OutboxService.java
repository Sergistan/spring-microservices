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

/**
 * Запись событий заказа в таблицу outbox В ТОЙ ЖЕ транзакции, что и изменение заказа:
 * либо заказ и событие фиксируются вместе, либо ничего. Сама публикация в Kafka —
 * задача OutboxRelay (см.); эта связка и называется transactional outbox.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Сериализует событие в JSON и сохраняет в outbox в ТЕКУЩЕЙ транзакции вызывающего
     * (поэтому без @Transactional здесь: запись фиксируется/откатывается вместе с заказом).
     * eventId генерируется при отсутствии — по нему консьюмеры дедуплицируют повторы.
     */
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
