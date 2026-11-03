package com.utochkin.shopservice.repositories;

import com.utochkin.shopservice.models.Product;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
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
                classes = ProductRepository.class
        )
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@ActiveProfiles("test")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Product persistProduct(UUID articleId, String name, int qty, double price) {
        Product p = Product.builder()
                .articleId(articleId)
                .name(name)
                .quantity(qty)
                .price(price)
                .build();
        em.persist(p);
        em.flush();
        return p;
    }

    @Test
    @DisplayName("findAllByArticleIds → возвращает все сущности по списку существующих articleId")
    void findAllByArticleIds_withExistingIds_returnsList() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Product p1 = persistProduct(id1, "A", 5, 100.0);
        Product p2 = persistProduct(id2, "B", 3, 50.0);
        // и ещё один, не в запросе
        persistProduct(UUID.randomUUID(), "X", 1, 10.0);

        List<Product> found = productRepository.findAllByArticleIds(List.of(id1, id2));

        assertThat(found)
                .hasSize(2)
                .extracting(Product::getArticleId)
                .containsExactlyInAnyOrder(id1, id2);
    }

    @Test
    @DisplayName("findAllByArticleIds → для несуществующих articleId возвращает пустой список")
    void findAllByArticleIds_withNonExistingIds_returnsEmpty() {
        persistProduct(UUID.randomUUID(), "A", 5, 100.0);

        List<Product> found = productRepository.findAllByArticleIds(List.of(UUID.randomUUID()));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findAllByArticleIds → пустой входной список даёт пустой результат")
    void findAllByArticleIds_withEmptyList_returnsEmpty() {
        persistProduct(UUID.randomUUID(), "A", 5, 100.0);

        List<Product> found = productRepository.findAllByArticleIds(List.of());

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByArticleId → существующий артикул возвращает Optional с сущностью")
    void findByArticleId_existing_returnsProduct() {
        UUID id = UUID.randomUUID();
        Product saved = persistProduct(id, "C", 7, 70.0);

        Optional<Product> result = productRepository.findByArticleId(id);

        assertThat(result).isPresent()
                .get()
                .extracting(Product::getName, Product::getPrice)
                .containsExactly("C", 70.0);
    }

    @Test
    @DisplayName("findByArticleId → несуществующий артикул возвращает Optional.empty()")
    void findByArticleId_nonExisting_returnsEmpty() {
        persistProduct(UUID.randomUUID(), "C", 7, 70.0);

        Optional<Product> result = productRepository.findByArticleId(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("deleteByArticleId → удаляет запись, после чего findByArticleId возвращает пусто")
    void deleteByArticleId_removesRecord() {
        UUID id = UUID.randomUUID();
        persistProduct(id, "D", 2, 20.0);

        assertThat(productRepository.findByArticleId(id)).isPresent();

        productRepository.deleteByArticleId(id);
        em.flush();
        em.clear();

        assertThat(productRepository.findByArticleId(id)).isEmpty();
    }

    @Test
    @DisplayName("deductStock → при достаточном остатке атомарно уменьшает и возвращает 1")
    void deductStock_sufficient_updatesQuantity() {
        UUID id = UUID.randomUUID();
        persistProduct(id, "E", 10, 100.0);

        int updated = transactionTemplate.execute(status -> productRepository.deductStock(id, 7));

        assertThat(updated).isEqualTo(1);
        em.clear();
        assertThat(productRepository.findByArticleId(id))
                .get()
                .extracting(Product::getQuantity)
                .isEqualTo(3);
    }

    @Test
    @DisplayName("deductStock → при недостаточном остатке возвращает 0 и не меняет баланс")
    void deductStock_insufficient_returnsZero() {
        UUID id = UUID.randomUUID();
        persistProduct(id, "E", 5, 100.0);

        int updated = transactionTemplate.execute(status -> productRepository.deductStock(id, 7));

        assertThat(updated).isZero();
        em.clear();
        assertThat(productRepository.findByArticleId(id))
                .get()
                .extracting(Product::getQuantity)
                .isEqualTo(5);
    }

    @Test
    @DisplayName("deductStock → несуществующий товар возвращает 0")
    void deductStock_missingProduct_returnsZero() {
        persistProduct(UUID.randomUUID(), "E", 5, 100.0);

        int updated = transactionTemplate.execute(status ->
                productRepository.deductStock(UUID.randomUUID(), 1));

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("restoreStock → атомарно увеличивает остаток и возвращает 1")
    void restoreStock_increasesQuantity() {
        UUID id = UUID.randomUUID();
        persistProduct(id, "F", 3, 100.0);

        int updated = transactionTemplate.execute(status -> productRepository.restoreStock(id, 4));

        assertThat(updated).isEqualTo(1);
        em.clear();
        assertThat(productRepository.findByArticleId(id))
                .get()
                .extracting(Product::getQuantity)
                .isEqualTo(7);
    }

    @Test
    @DisplayName("restoreStock → несуществующий товар возвращает 0")
    void restoreStock_missingProduct_returnsZero() {
        int updated = transactionTemplate.execute(status ->
                productRepository.restoreStock(UUID.randomUUID(), 4));

        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("конкурентное списание одного товара: из остатка 10 два потока по 6 — успешна ровно одна операция, итог 4")
    void deductStock_concurrent_onlyOneSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        persistCommitted(Product.builder()
                .articleId(id)
                .name("G")
                .quantity(10)
                .price(100.0)
                .build());

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        Runnable task = () -> {
            try {
                start.await();
                Integer updated = transactionTemplate.execute(status -> productRepository.deductStock(id, 6));
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
        assertThat(productRepository.findByArticleId(id))
                .get()
                .extracting(Product::getQuantity)
                .isEqualTo(4);
    }

    private void persistCommitted(Product product) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> productRepository.save(product));
        em.clear();
    }
}
