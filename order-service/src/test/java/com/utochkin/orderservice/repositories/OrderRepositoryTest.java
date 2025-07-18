package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.models.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = OrderRepository.class
        )
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@ActiveProfiles("test")
public class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepo;

    @Test
    @DisplayName("Сохраняем и находим по UUID")
    void saveAndFindByOrderUuid() {
        UUID uuid = UUID.randomUUID();
        Order order = Order.builder()
                .orderUuid(uuid)
                .totalAmount(150.0)
                .orderStatus(Status.WAITING_FOR_PAYMENT)
                .createdAt(LocalDateTime.now())
                .build();

        orderRepo.save(order);

        Optional<Order> found = orderRepo.findByOrderUuid(uuid);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualTo(150.0);
        assertThat(found.get().getOrderStatus()).isEqualTo(Status.WAITING_FOR_PAYMENT);
    }

    @Test
    @DisplayName("findTotalAmountByOrderUuid возвращает корректную сумму")
    void findTotalAmountByOrderUuid() {
        UUID uuid = UUID.randomUUID();
        Order order = Order.builder()
                .orderUuid(uuid)
                .totalAmount(321.99)
                .orderStatus(Status.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();

        orderRepo.save(order);

        Double total = orderRepo.findTotalAmountByOrderUuid(uuid);
        assertThat(total).isEqualTo(321.99);
    }

    @Test
    @DisplayName("findTotalAmountByOrderUuid возвращает null, если нет")
    void findTotalAmountForMissing() {
        Double total = orderRepo.findTotalAmountByOrderUuid(UUID.randomUUID());
        assertThat(total).isNull();
    }

}
