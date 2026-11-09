package com.utochkin.orderservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.models.OutboxEvent;
import com.utochkin.orderservice.repositories.OutboxRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
public class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private OutboxRelay outboxRelay;

    private OutboxEvent event(UUID eventId, UUID orderUuid) {
        OrderDtoForKafka dto = new OrderDtoForKafka(
                orderUuid, BigDecimal.valueOf(100.0), com.utochkin.orderservice.models.Status.SUCCESS,
                LocalDateTime.now(), LocalDateTime.now(), null, null, null, UUID.randomUUID(), eventId);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return OutboxEvent.builder()
                .id(1L)
                .eventId(eventId)
                .orderUuid(orderUuid)
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        SendResult<String, Object> result = mock(SendResult.class);
        lenient().when(result.getRecordMetadata()).thenReturn(mock(RecordMetadata.class));
        CompletableFuture<SendResult<String, Object>> success = CompletableFuture.completedFuture(result);
        lenient().when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(success);
        lenient().doAnswer(invocation -> {
                    Consumer<TransactionStatus> callback = invocation.getArgument(0);
                    callback.accept(mock(TransactionStatus.class));
                    return null;
                })
                .when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("pollOnce: отправляет с ключом orderUuid и удаляет строку после успешного ack")
    void pollOnce_success_sendsWithKeyAndDeletes() {
        UUID eventId = UUID.randomUUID();
        UUID orderUuid = UUID.randomUUID();
        OutboxEvent outboxEvent = event(eventId, orderUuid);
        when(outboxRepository.lockBatch()).thenReturn(List.of(outboxEvent));

        outboxRelay.pollOnce();

        verify(kafkaTemplate).send(eq("topic-orders"), eq(orderUuid.toString()), any(OrderDtoForKafka.class));
        verify(outboxRepository).delete(outboxEvent);
    }

    @Test
    @DisplayName("pollOnce: при сбое отправки строка остаётся в outbox (retry на следующем цикле)")
    void pollOnce_sendFails_keepsRow() {
        UUID orderUuid = UUID.randomUUID();
        OutboxEvent outboxEvent = event(UUID.randomUUID(), orderUuid);
        when(outboxRepository.lockBatch()).thenReturn(List.of(outboxEvent));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        outboxRelay.pollOnce();

        verify(outboxRepository, never()).delete(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("pollOnce: батч обрабатывается в порядке id, сбой прерывает дальнейшую отправку")
    void pollOnce_failureAbortsBatch_preservingOrder() {
        OutboxEvent first = event(UUID.randomUUID(), UUID.randomUUID());
        first.setId(1L);
        OutboxEvent second = event(UUID.randomUUID(), UUID.randomUUID());
        second.setId(2L);
        when(outboxRepository.lockBatch()).thenReturn(List.of(first, second));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);

        outboxRelay.pollOnce();

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        verify(outboxRepository, never()).delete(any(OutboxEvent.class));
        assertThat(first.getId()).isEqualTo(1L);
        assertThat(second.getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("pollOnce: пустой outbox — нет обращений к kafka")
    void pollOnce_emptyOutbox_noSends() {
        when(outboxRepository.lockBatch()).thenReturn(List.of());

        outboxRelay.pollOnce();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxRepository, never()).delete(any());
    }
}
