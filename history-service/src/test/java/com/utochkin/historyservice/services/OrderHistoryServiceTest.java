package com.utochkin.historyservice.services;

import com.utochkin.historyservice.dto.AddressDto;
import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.dto.UserDto;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.models.OrderRequest;
import com.utochkin.historyservice.models.Status;
import com.utochkin.historyservice.repositories.OrderHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderHistoryServiceTest {

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @InjectMocks
    private OrderHistoryService orderHistoryService;

    private OrderDtoForKafka orderDto;

    @BeforeEach
    void setup() {
        orderDto = new OrderDtoForKafka(
                UUID.randomUUID(),
                120.0,
                Status.SUCCESS,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                new AddressDto("City", "Street", 1, 1),
                new UserDto("john", "John", "Doe", "john@example.com"),
                List.of(new OrderRequest(UUID.randomUUID(), 2)),
                UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("Создание нового OrderHistory для нового пользователя и его дальнейшее сохранение")
    void consume_createsNewOrderHistory_ifUserNotExists() {
        when(orderHistoryRepository.findByUsername("john"))
                .thenReturn(Optional.empty());

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
        OrderHistory existing = new OrderHistory("john", new ArrayList<>());
        when(orderHistoryRepository.findByUsername("john"))
                .thenReturn(Optional.of(existing));

        orderHistoryService.consume(orderDto);

        verify(orderHistoryRepository).save(existing);
        assertThat(existing.getOrders()).containsExactly(orderDto);
    }
}
