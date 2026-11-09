package com.utochkin.orderservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.models.OutboxEvent;
import com.utochkin.orderservice.repositories.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
public class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private OutboxService outboxService;

    private OrderDtoForKafka dto;

    @BeforeEach
    void setUp() {
        dto = new OrderDtoForKafka(UUID.randomUUID(), BigDecimal.valueOf(100.0),
                com.utochkin.orderservice.models.Status.SUCCESS,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null,
                UUID.randomUUID(),
                null
        );
    }

    @Test
    @DisplayName("saveToOutbox: генерирует eventId, сохраняет payload c eventId и orderUuid")
    void saveToOutbox_generatesEventIdAndStoresPayload() {
        outboxService.saveToOutbox(dto);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getEventId()).isNotNull();
        assertThat(dto.getEventId()).isEqualTo(saved.getEventId());
        assertThat(saved.getOrderUuid()).isEqualTo(dto.getOrderUuid());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPayload())
                .contains("\"orderUuid\":\"" + dto.getOrderUuid() + "\"")
                .contains("\"eventId\":\"" + saved.getEventId() + "\"");
    }

    @Test
    @DisplayName("saveToOutbox: использует переданный eventId, не перегенерируя его")
    void saveToOutbox_keepsExistingEventId() {
        UUID eventId = UUID.randomUUID();
        dto.setEventId(eventId);

        outboxService.saveToOutbox(dto);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("saveToOutbox: при ошибке сериализации бросает IllegalStateException (откат tx)")
    void saveToOutbox_serializationFailure_throws() throws Exception {
        org.mockito.Mockito.doThrow(new com.fasterxml.jackson.core.JsonProcessingException("json failure") {})
                .when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> outboxService.saveToOutbox(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Не удалось сериализовать");
        verify(outboxRepository, org.mockito.Mockito.never()).save(any(OutboxEvent.class));
    }
}
