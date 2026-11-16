package com.utochkin.historyservice.services;

import com.utochkin.historyservice.dto.AddressDto;
import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.dto.UserDto;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.models.ProcessedEvent;
import com.utochkin.historyservice.repositories.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class OrderHistoryServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private OrderHistoryService orderHistoryService;

    private OrderDtoForKafka orderDto;

    @BeforeEach
    void setup() {
        orderDto = new OrderDtoForKafka(
                UUID.randomUUID(),
                BigDecimal.valueOf(120.0),
                com.utochkin.historyservice.models.Status.SUCCESS,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                new AddressDto("City", "Street", 1, 1),
                new UserDto("john", "John", "Doe", "john@example.com"),
                List.of(new com.utochkin.historyservice.models.OrderRequest(UUID.randomUUID(), 2)),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("Запись истории — атомарный upsert с $push в orders по username")
    void consume_upsertsWithPush() {
        doReturn(null).when(processedEventRepository).insert(any(ProcessedEvent.class));

        orderHistoryService.consume(orderDto, ack);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).upsert(any(Query.class), updateCaptor.capture(), eq(OrderHistory.class));

        Update update = updateCaptor.getValue();
        Object push = update.getUpdateObject().get("$push");
        assertThat(push)
                .as("в $push по ключу orders лежит именно событие текущего заказа")
                .isInstanceOfSatisfying(Document.class, pushDoc ->
                        assertThat(pushDoc.get("orders")).isEqualTo(orderDto));

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Повторное событие (DuplicateKeyException в маркере) — история не пишется, offset ack")
    void consume_duplicateEvent_skipped() {
        when(processedEventRepository.insert(any(ProcessedEvent.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key error"));

        assertThatCode(() -> orderHistoryService.consume(orderDto, ack))
                .doesNotThrowAnyException();

        verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), any(Class.class));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Сбой сохранения истории — маркер обработанности удаляется (компенсация), исключение наружу")
    void consume_historySaveFails_compensatesMarker() {
        doReturn(null).when(processedEventRepository).insert(any(ProcessedEvent.class));
        doThrow(new RuntimeException("mongo down")).when(mongoTemplate)
                .upsert(any(Query.class), any(Update.class), eq(OrderHistory.class));

        assertThatThrownBy(() -> orderHistoryService.consume(orderDto, ack))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mongo down");

        verify(processedEventRepository).deleteById(orderDto.getEventId().toString());
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("Событие без eventId обрабатывается без дедупликации")
    void consume_nullEventId_processedWithoutMarker() {
        orderDto.setEventId(null);

        orderHistoryService.consume(orderDto, ack);

        verify(processedEventRepository, never()).insert(any(ProcessedEvent.class));
        verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(OrderHistory.class));
        verify(ack).acknowledge();
    }
}
