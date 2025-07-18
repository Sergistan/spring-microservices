package com.utochkin.historyservice.repositories;
import com.utochkin.historyservice.dto.AddressDto;
import com.utochkin.historyservice.dto.OrderDtoForKafka;
import com.utochkin.historyservice.dto.UserDto;
import com.utochkin.historyservice.models.OrderHistory;
import com.utochkin.historyservice.models.OrderRequest;
import com.utochkin.historyservice.models.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ActiveProfiles("test")
public class OrderHistoryRepositoryTest {

    @Autowired
    private OrderHistoryRepository repository;

    private final String username = "alice";

    private OrderDtoForKafka orderDto;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        orderDto = new OrderDtoForKafka(
                UUID.randomUUID(),
                120.0,
                Status.SUCCESS,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                new AddressDto("Moscow", "Arbat", 12, 45),
                new UserDto(username, "Alice", "Smith", "alice@example.com"),
                List.of(new OrderRequest(UUID.randomUUID(), 2)),
                UUID.randomUUID()
        );

        OrderHistory history = new OrderHistory(username, List.of(orderDto));
        repository.save(history);
    }

    @Test
    @DisplayName("Возвращает историю заказов по имени пользователя")
    void findByUsername_returnsOrderHistory() {
        Optional<OrderHistory> result = repository.findByUsername(username);

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo(username);
        assertThat(result.get().getOrders()).hasSize(1);
        assertThat(result.get().getOrders().get(0).getOrderUuid()).isEqualTo(orderDto.getOrderUuid());
    }

    @Test
    @DisplayName("Возвращает пусто, если история отсутствует")
    void findByUsername_returnsEmptyIfNotExists() {
        Optional<OrderHistory> result = repository.findByUsername("bob");

        assertThat(result).isNotPresent();
    }

    @Test
    @DisplayName("Сохраняет несколько заказов одному пользователю")
    void save_multipleOrders() {
        OrderDtoForKafka secondOrder = new OrderDtoForKafka(
                UUID.randomUUID(),
                230.0,
                Status.FAILED,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now(),
                new AddressDto("Moscow", "Tverskaya", 7, 2),
                new UserDto(username, "Alice", "Smith", "alice@example.com"),
                List.of(new OrderRequest(UUID.randomUUID(), 1)),
                UUID.randomUUID()
        );

        OrderHistory history = repository.findByUsername(username).orElseThrow();
        history.getOrders().add(secondOrder);
        repository.save(history);

        OrderHistory updated = repository.findByUsername(username).orElseThrow();
        assertThat(updated.getOrders()).hasSize(2);
    }

}
