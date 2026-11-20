package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.models.Role;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.models.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;

@DataJpaTest(
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = OrderRepository.class
        )
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@ActiveProfiles("test")
public class OrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("Сохраняем и находим по UUID")
    void saveAndFindByOrderUuid() {
        UUID uuid = UUID.randomUUID();
        Order order = Order.builder()
                .orderUuid(uuid)
                .totalAmount(BigDecimal.valueOf(150.0))
                .orderStatus(Status.WAITING_FOR_PAYMENT)
                .createdAt(LocalDateTime.now())
                .build();

        orderRepo.save(order);

        Optional<Order> found = orderRepo.findByOrderUuid(uuid);
        assertThat(found).isPresent();
        assertThat(found.get().getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(150.0));
        assertThat(found.get().getOrderStatus()).isEqualTo(Status.WAITING_FOR_PAYMENT);
    }

    @Test
    @DisplayName("findTotalAmountByOrderUuid возвращает корректную сумму")
    void findTotalAmountByOrderUuid() {
        UUID uuid = UUID.randomUUID();
        Order order = Order.builder()
                .orderUuid(uuid)
                .totalAmount(BigDecimal.valueOf(321.99))
                .orderStatus(Status.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();

        orderRepo.save(order);

        BigDecimal total = orderRepo.findTotalAmountByOrderUuid(uuid);
        assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(321.99));
    }

    @Test
    @DisplayName("findTotalAmountByOrderUuid возвращает null, если нет")
    void findTotalAmountForMissing() {
        BigDecimal total = orderRepo.findTotalAmountByOrderUuid(UUID.randomUUID());
        assertThat(total).isNull();
    }

    @Test
    @DisplayName("transitionStatus: WAITING_FOR_PAYMENT → PROCESSING возвращает 1 и фиксирует статус")
    void transitionStatus_fromWaiting_updatesRow() {
        UUID uuid = UUID.randomUUID();
        orderRepo.save(Order.builder()
                .orderUuid(uuid)
                .totalAmount(BigDecimal.valueOf(100.0))
                .orderStatus(Status.WAITING_FOR_PAYMENT)
                .createdAt(LocalDateTime.now())
                .build());
        orderRepo.flush();

        int updated = orderRepo.transitionStatus(uuid,
                List.of(Status.WAITING_FOR_PAYMENT, Status.FAILED, Status.PROCESSING), Status.PROCESSING);

        assertThat(updated).isEqualTo(1);
        em.clear();
        assertThat(orderRepo.findByOrderUuid(uuid))
                .get()
                .extracting(Order::getOrderStatus)
                .isEqualTo(Status.PROCESSING);
    }

    @Test
    @DisplayName("transitionStatus: повторный вызов из PROCESSING (повторная попытка) возвращает 1")
    void transitionStatus_fromProcessing_againAllowed() {
        UUID uuid = UUID.randomUUID();
        orderRepo.save(Order.builder()
                .orderUuid(uuid)
                .totalAmount(BigDecimal.valueOf(100.0))
                .orderStatus(Status.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build());
        orderRepo.flush();

        int updated = orderRepo.transitionStatus(uuid,
                List.of(Status.WAITING_FOR_PAYMENT, Status.FAILED, Status.PROCESSING), Status.PROCESSING);

        assertThat(updated).isEqualTo(1);
    }

    @Test
    @DisplayName("transitionStatus: SUCCESS не входит в allowed → 0 строк (двойная оплата отклонена)")
    void transitionStatus_successBlocked_returnsZero() {
        UUID uuid = UUID.randomUUID();
        orderRepo.save(Order.builder()
                .orderUuid(uuid)
                .totalAmount(BigDecimal.valueOf(100.0))
                .orderStatus(Status.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build());
        orderRepo.flush();

        int updated = orderRepo.transitionStatus(uuid,
                List.of(Status.WAITING_FOR_PAYMENT, Status.FAILED, Status.PROCESSING), Status.PROCESSING);

        assertThat(updated).isZero();
        em.clear();
        assertThat(orderRepo.findByOrderUuid(uuid))
                .get()
                .extracting(Order::getOrderStatus)
                .isEqualTo(Status.SUCCESS);
    }

    @Test
    @DisplayName("transitionStatus: для возврата разрешен только SUCCESS, PROCESSING → 0 строк")
    void transitionStatus_refundFromProcessing_returnsZero() {
        UUID uuid = UUID.randomUUID();
        orderRepo.save(Order.builder()
                .orderUuid(uuid)
                .totalAmount(BigDecimal.valueOf(100.0))
                .orderStatus(Status.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build());
        orderRepo.flush();

        int updated = orderRepo.transitionStatus(uuid, List.of(Status.SUCCESS), Status.PROCESSING);

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("transitionStatus: несуществующий заказ → 0 строк")
    void transitionStatus_missingOrder_returnsZero() {
        int updated = orderRepo.transitionStatus(UUID.randomUUID(),
                List.of(Status.WAITING_FOR_PAYMENT), Status.PROCESSING);
        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("existsByUser_SubIdAndOrderStatusNotIn: SUCCESS — активный, только финальные — не активный")
    void existsByUser_SubIdAndOrderStatusNotIn_activeVsFinal() {
        User activeUser = User.builder()
                .subId("sub-active").username("active_user")
                .firstName("A").lastName("A").email("a@a.com").role(Role.USER).build();
        User doneUser = User.builder()
                .subId("sub-done").username("done_user")
                .firstName("D").lastName("D").email("d@d.com").role(Role.USER).build();
        em.persist(activeUser);
        em.persist(doneUser);

        orderRepo.save(Order.builder()
                .orderUuid(UUID.randomUUID()).totalAmount(BigDecimal.valueOf(100.0))
                .orderStatus(Status.SUCCESS).createdAt(LocalDateTime.now()).user(activeUser).build());
        orderRepo.save(Order.builder()
                .orderUuid(UUID.randomUUID()).totalAmount(BigDecimal.valueOf(200.0))
                .orderStatus(Status.DELIVERED).createdAt(LocalDateTime.now()).user(activeUser).build());
        orderRepo.save(Order.builder()
                .orderUuid(UUID.randomUUID()).totalAmount(BigDecimal.valueOf(300.0))
                .orderStatus(Status.REFUNDED).createdAt(LocalDateTime.now()).user(doneUser).build());
        orderRepo.flush();

        List<Status> finalStatuses = List.of(Status.FAILED, Status.REFUNDED, Status.DELIVERED);

        // у activeUser есть SUCCESS (доставка предстоит) -> аккаунт удалять нельзя
        assertThat(orderRepo.existsByUser_SubIdAndOrderStatusNotIn("sub-active", finalStatuses)).isTrue();
        // у doneUser только финальные статусы -> аккаунт можно удалять
        assertThat(orderRepo.existsByUser_SubIdAndOrderStatusNotIn("sub-done", finalStatuses)).isFalse();
        assertThat(orderRepo.existsByUser_SubIdAndOrderStatusNotIn("sub-unknown", finalStatuses)).isFalse();
    }

}
