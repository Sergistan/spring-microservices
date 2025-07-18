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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
