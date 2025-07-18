package com.utochkin.orderservice.services;

import com.utochkin.orderservice.dto.OrderDtoForKafka;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

public class KafkaSenderServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaSenderService kafkaSenderService;

    private OrderDtoForKafka dto;

    private CompletableFuture<SendResult<String, Object>> successfulFuture;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        dto = new OrderDtoForKafka(
                UUID.randomUUID(),
                42.0,
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                null,
                UUID.randomUUID()
        );

        // Замокаем SendResult без создания RecordMetadata
        @SuppressWarnings("unchecked")
        SendResult<String, Object> fakeResult = mock(SendResult.class);

        // CompletableFuture с «успешным» результатом
        successfulFuture = CompletableFuture.completedFuture(fakeResult);

        // kafkaTemplate.send(...) → наш готовый Future
        when(kafkaTemplate.send(anyString(), any())).thenReturn(successfulFuture);
    }

    @Test
    @DisplayName("Успешная отправка в kafka")
    void send_ShouldInvokeKafkaTemplateSend() {
        // Act
        kafkaSenderService.send(dto);

        // Assert
        verify(kafkaTemplate, times(1)).send("topic-orders", dto);

        when(kafkaTemplate.send(anyString(), any())).thenReturn(successfulFuture);
    }

    @Test
    @DisplayName("Неуспешная отправка в kafka все равно должна вызывать отправку")
    void send_WhenSendFails_ShouldStillInvokeSend() {
        // Arrange: Future, завершающийся ошибкой
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("boom"));
        when(kafkaTemplate.send(anyString(), any())).thenReturn(failedFuture);

        // Act
        kafkaSenderService.send(dto);

        // Assert: метод send() всё равно был вызван
        verify(kafkaTemplate).send("topic-orders", dto);
    }
}
