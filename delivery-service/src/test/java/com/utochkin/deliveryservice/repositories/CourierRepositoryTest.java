package com.utochkin.deliveryservice.repositories;

import com.utochkin.deliveryservice.models.Courier;
import com.utochkin.deliveryservice.models.CourierStatus;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {CourierRepository.class, DeliveryRepository.class}
        )
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public class CourierRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private CourierRepository courierRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("tryAssign: конкурентное назначение одного курьера двумя потоками — успешен ровно один")
    void tryAssign_concurrent_onlyOneSucceeds() throws Exception {
        persistCommitted(Courier.builder()
                .subId(UUID.randomUUID()).username("courier-1")
                .status(CourierStatus.AVAILABLE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
        em.clear();
        Courier saved = courierRepository.findAll().get(0);

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        Runnable task = () -> {
            try {
                start.await();
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                Integer updated = tx.execute(status -> courierRepository.tryAssign(saved.getId()));
                if (updated != null && updated == 1) {
                    successes.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = executor.submit(task);
            Future<?> f2 = executor.submit(task);
            start.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        em.clear();
        assertThat(courierRepository.findById(saved.getId()))
                .get()
                .extracting(Courier::getStatus)
                .isEqualTo(CourierStatus.BUSY);
    }

    @Test
    @DisplayName("tryAssign на BUSY-курьера → 0 строк; release возвращает в AVAILABLE")
    void tryAssign_busyRejected_releaseRestores() {
        persistCommitted(Courier.builder()
                .subId(UUID.randomUUID()).username("courier-2")
                .status(CourierStatus.BUSY)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
        em.clear();
        Courier busy = courierRepository.findAll().get(0);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> assertThat(courierRepository.tryAssign(busy.getId())).isZero());
        tx.executeWithoutResult(status -> assertThat(courierRepository.release(busy.getId())).isEqualTo(1));

        em.clear();
        assertThat(courierRepository.findById(busy.getId()))
                .get()
                .extracting(Courier::getStatus)
                .isEqualTo(CourierStatus.AVAILABLE);
    }

    @Test
    @DisplayName("findByStatusOrderByIdAsc: только AVAILABLE, в порядке регистрации")
    void findByStatus_returnsAvailableSorted() {
        persistCommitted(Courier.builder().subId(UUID.randomUUID()).username("c-1")
                .status(CourierStatus.AVAILABLE).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        persistCommitted(Courier.builder().subId(UUID.randomUUID()).username("c-2")
                .status(CourierStatus.BUSY).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        persistCommitted(Courier.builder().subId(UUID.randomUUID()).username("c-3")
                .status(CourierStatus.AVAILABLE).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        em.clear();

        // берём только «своих» курьеров этого теста: соседние тесты тоже коммитят данные (REQUIRES_NEW)
        List<Courier> free = courierRepository.findByStatusOrderByIdAsc(CourierStatus.AVAILABLE).stream()
                .filter(c -> c.getUsername().startsWith("c-"))
                .toList();

        assertThat(free).extracting(Courier::getUsername).containsExactly("c-1", "c-3");
    }

    @Test
    @DisplayName("findByCourierIdAndStatusIn: исторические доставки не мешают удалению осиротевшего курьера")
    void findByCourierIdAndStatusIn_activeOnly() {
        persistCommitted(Courier.builder().subId(UUID.randomUUID()).username("orphan")
                .status(CourierStatus.AVAILABLE).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        persistCommitted(Courier.builder().subId(UUID.randomUUID()).username("busy")
                .status(CourierStatus.BUSY).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        em.clear();
        Courier orphan = courierRepository.findAll().stream()
                .filter(c -> c.getUsername().equals("orphan")).findFirst().orElseThrow();
        Courier busy = courierRepository.findAll().stream()
                .filter(c -> c.getUsername().equals("busy")).findFirst().orElseThrow();

        persistDelivery(orphan.getId(), com.utochkin.deliveryservice.models.DeliveryStatus.DELIVERED);
        persistDelivery(busy.getId(), com.utochkin.deliveryservice.models.DeliveryStatus.IN_DELIVERY);

        List<com.utochkin.deliveryservice.models.DeliveryStatus> active = List.of(
                com.utochkin.deliveryservice.models.DeliveryStatus.SEARCHING_COURIER,
                com.utochkin.deliveryservice.models.DeliveryStatus.ASSIGNED,
                com.utochkin.deliveryservice.models.DeliveryStatus.IN_DELIVERY);

        // у orphan только историческая DELIVERED — активных нет (можно удалять из справочника)
        assertThat(deliveryRepository.findByCourierIdAndStatusIn(orphan.getId(), active)).isEmpty();
        // у busy активная IN_DELIVERY — удалять нельзя
        assertThat(deliveryRepository.findByCourierIdAndStatusIn(busy.getId(), active)).hasSize(1);
    }

    private void persistDelivery(Long courierId,
                                 com.utochkin.deliveryservice.models.DeliveryStatus status) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(tx -> deliveryRepository.save(
                com.utochkin.deliveryservice.models.Delivery.builder()
                        .orderUuid(UUID.randomUUID())
                        .courierId(courierId)
                        .status(status)
                        .paidAt(LocalDateTime.now().minusMinutes(10))
                        .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                        .build()));
        em.clear();
    }

    private void persistCommitted(Courier courier) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> courierRepository.save(courier));
        em.clear();
    }
}
