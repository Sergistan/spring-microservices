package com.utochkin.shopservice;

import com.utochkin.shopservice.config.AuthServerRoleConverter;
import com.utochkin.shopservice.config.Config;
import com.utochkin.shopservice.web.ProductDtoRequest;
import com.utochkin.shopservice.exceptions.CustomAccessDeniedHandler;
import com.utochkin.shopservice.models.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.shopservice.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = ShopServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Import({
        Config.class,
        AuthServerRoleConverter.class,
        CustomAccessDeniedHandler.class,  
        ShopServiceApplicationTests.TestCacheConfig.class
})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.cloud.compatibility-verifier.enabled=false",
        "spring.liquibase.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@WithMockUser(roles = "ADMIN")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ShopServiceApplicationTests {

    // Реальный PostgreSQL в docker-контейнере: @ServiceConnection подставляет реквизиты
    // dataSource в тестовый контекст (вместо встроенной H2; схема создаётся Hibernate)
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private ProductRepository productRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UUID existingId;

    @TestConfiguration
    @EnableCaching
    static class TestCacheConfig {
        @Bean
        @Primary
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("products", "product");
        }
    }

    @BeforeEach
    void setup() {
        productRepository.deleteAll();
        Product p = new Product();
        p.setArticleId(UUID.randomUUID());
        p.setName("Phone");
        p.setQuantity(10);
        p.setPrice(BigDecimal.valueOf(499.99));
        existingId = p.getArticleId();
        productRepository.save(p);
    }

    @Test
    @DisplayName("GET /shop/api/v1/getAllProducts — возвращает выгруженные продукты")
    void getAllProducts_returnsProducts() throws Exception {
        mvc.perform(get("/shop/api/v1/products")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].articleId").value(existingId.toString()))
                .andExpect(jsonPath("$[0].name").value("Phone"));
    }

    @Test
    @DisplayName("GET /shop/api/v1/getProducts/{id} — возвращает один продукт")
    void getProduct_returnsDto() throws Exception {
        mvc.perform(get("/shop/api/v1/products/{id}", existingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(existingId.toString()))
                .andExpect(jsonPath("$.name").value("Phone"));
    }

    @Test
    @DisplayName("GET /shop/api/v1/getProducts/{id} — возвращает 404 при отсутствии")
    void getProduct_notFound() throws Exception {
        mvc.perform(get("/shop/api/v1/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /shop/api/v1/addProduct — создает продукт и возвращает dto с 201")
    void addProduct_creates() throws Exception {
        ProductDtoRequest req = new ProductDtoRequest("Laptop", 5, BigDecimal.valueOf(1299.0));
        String json = mapper.writeValueAsString(req);

        mvc.perform(post("/shop/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.articleId").exists())
                .andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.quantity").value(5));

        assertThat(productRepository.findAllByArticleIds(
                productRepository.findAll().stream().map(Product::getArticleId).toList()
        )).hasSize(2);
    }

    @Test
    @DisplayName("DELETE /shop/api/v1/deleteProduct/{id} — удаляет продукт и возвращает 200")
    void deleteProduct_removes() throws Exception {
        mvc.perform(delete("/shop/api/v1/products/{id}", existingId))
                .andExpect(status().isOk())
                .andExpect(content().string("Товар с articleId = " + existingId + " успешно удален"));

        assertThat(productRepository.findByArticleId(existingId)).isEmpty();
    }

    @Test
    @DisplayName("DELETE /shop/api/v1/deleteProduct/{id} — 404 если отсутствует")
    void deleteProduct_notFound() throws Exception {
        mvc.perform(delete("/shop/api/v1/products/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /shop/api/v1/updateProduct/{id} — обновляет и возвращает dto")
    void updateProduct_updates() throws Exception {
        ProductDtoRequest req = new ProductDtoRequest("PhoneX", 20, BigDecimal.valueOf(599.0));
        String json = mapper.writeValueAsString(req);

        mvc.perform(put("/shop/api/v1/products/{id}", existingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleId").value(existingId.toString()))
                .andExpect(jsonPath("$.name").value("PhoneX"))
                .andExpect(jsonPath("$.quantity").value(20));

        Product updated = productRepository.findByArticleId(existingId).orElseThrow();
        assertThat(updated.getQuantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("PUT /shop/api/v1/updateProduct/{id} — 404 если отсутствует")
    void updateProduct_notFound() throws Exception {
        ProductDtoRequest req = new ProductDtoRequest("X", 1, BigDecimal.valueOf(10.0));
        mvc.perform(put("/shop/api/v1/products/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }
}
