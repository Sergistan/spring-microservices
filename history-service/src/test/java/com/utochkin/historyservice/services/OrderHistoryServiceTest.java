package com.utochkin.historyservice.services;

import com.utochkin.historyservice.dto.AddressDto;
import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.dto.UserDto;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.models.ProcessedEvent;
import com.utochkin.historyservice.repositories.OrderHistoryRepository;
import com.utochkin.historyservice.repositories.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderHistoryServiceTest {

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private OrderHistoryService orderHistoryService;

    private OrderDtoForKafka orderDto;

    @BeforeEach
    void setup() {
        orderDto = new OrderDtoForKafka(
                UUID.randomUUID(),
                java.math.BigDecimal.valueOf(120.0),
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
    @DisplayName("Создание нового OrderHistory для нового пользователя и его дальнейшее сохранение")
    void consume_createsNewOrderHistory_ifUserNotExists() {
        doReturn(null).when(processedEventRepository).insert(any(ProcessedEvent.class));
        when(orderHistoryRepository.findByUsername("john"))
                .thenReturn(java.util.Optional.empty());

        orderHistoryService.consume(orderDto);

        ArgumentCaptor<OrderHistory> captor = ArgumentCaptor.forClass(OrderHistory.class);
        verify(orderHistoryRepository).save(captor.capture());

        OrderHistory saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("john");
        assertThat(saved.getOrders()).hasSize(1);
        assertThat(saved.getOrders().getFirst()).isEqualTo(orderDto);
    }

    @Test
    @DisplayName("Корректное сохранение в OrderHistory для существующего пользователя")
    void consume_appendsOrder_ifUserExists() {
        doReturn(null).when(processedEventRepository).insert(any(ProcessedEvent.class));
        OrderHistory existing = new OrderHistory("john", new ArrayList<>());
        when(orderHistoryRepository.findByUsername("john"))
                .thenReturn(java.util.Optional.of(existing));

        orderHistoryService.consume(orderDto);

        verify(orderHistoryRepository).save(existing);
        assertThat(existing.getOrders()).containsExactly(orderDto);
    }

    @Test
    @DisplayName("Повторное событие (DuplicateKeyException в маркере) — история не пишется, offset ack")
    void consume_duplicateEvent_skipped() {
        when(processedEventRepository.insert(any(ProcessedEvent.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key error"));

        assertThatCode(() -> orderHistoryService.consume(orderDto))
                .doesNotThrowAnyException();

        verify(orderHistoryRepository, never()).save(any(OrderHistory.class));
    }

    @Test
    @DisplayName("Сбой сохранения истории — маркер обработанности удаляется (компенсация), исключение наружу")
    void consume_historySaveFails_compensatesMarker() {
        doReturn(null).when(processedEventRepository).insert(any(ProcessedEvent.class));
        when(orderHistoryRepository.findByUsername("john"))
                .thenReturn(java.util.Optional.of(new OrderHistory("john", new ArrayList<>())));
        when(orderHistoryRepository.save(any(OrderHistory.class)))
                .thenThrow(new RuntimeException("mongo down"));

        assertThatThrownBy(() -> orderHistoryService.consume(orderDto))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("mongo down");

        verify(processedEventRepository).deleteById(orderDto.getEventId().toString());
    }

    @Test
    @DisplayName("Событие без eventId обрабатывается без дедупликации")
    void consume_nullEventId_processedWithoutMarker() {
        orderDto.setEventId(null);
        when(orderHistoryRepository.findByUsername("john"))
                .thenReturn(java.util.Optional.of(new OrderHistory("john", new ArrayList<>())));

        orderHistoryService.consume(orderDto);

        verify(processedEventRepository, never()).insert(any(ProcessedEvent.class));
        verify(orderHistoryRepository).save(any(OrderHistory.class));
    }
}
