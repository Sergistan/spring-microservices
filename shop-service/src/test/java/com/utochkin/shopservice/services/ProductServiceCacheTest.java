package com.utochkin.shopservice.services;

import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.dto.ProductDtoRequest;
import com.utochkin.shopservice.mappers.ProductMapper;
import com.utochkin.shopservice.models.Product;
import com.utochkin.shopservice.repositories.ProductRepository;
import com.utochkin.shopservice.repositories.StockOperationRepository;
import com.utochkin.shopservice.requests.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(SpringExtension.class)
@Import(ProductService.class)
class ProductServiceCacheTest {

    @Autowired
    private ProductService productService;

    @MockitoBean
    private ProductRepository productRepo;

    @MockitoBean
    private StockOperationRepository stockOperationRepo;

    @MockitoBean
    private ProductMapper productMapper;

    @Autowired
    private CacheManager cacheManager;

    private Pageable pageable;
    private Product sampleEntity;
    private ProductDto sampleDto;
    private UUID sampleId;

    @TestConfiguration
    @EnableCaching
    public static class CacheTestConfig {
        @Bean
        public CacheManager cacheManager() {
            // создаём in‑memory кэши "products" и "product"
            return new ConcurrentMapCacheManager("products", "product");
        }
    }

    @BeforeEach
    void setUp() {
        sampleId = UUID.randomUUID();
        pageable = PageRequest.of(0, 2, Sort.by("name").ascending());

        sampleEntity = new Product();
        sampleEntity.setArticleId(sampleId);
        sampleEntity.setName("A");
        sampleEntity.setQuantity(5);
        sampleEntity.setPrice(BigDecimal.valueOf(10.0));

        sampleDto = new ProductDto(sampleId, "A", 5, BigDecimal.valueOf(10.0));

        // очистим кеши перед каждым тестом
        cacheManager.getCache("product").clear();
        cacheManager.getCache("products").clear();
    }

    @Test
    @DisplayName("кэширование на getAllProducts")
    void caching_getAllProducts() {
        // given
        Page<Product> page = new PageImpl<>(List.of(sampleEntity), pageable, 1);
        given(productRepo.findAll(pageable)).willReturn(page);
        given(productMapper.toListDto(page.getContent())).willReturn(List.of(sampleDto));

        // when — первый вызов
        List<ProductDto> first = productService.getAllProducts(pageable);
        // и второй
        List<ProductDto> second = productService.getAllProducts(pageable);

        // then
        assertThat(first).isEqualTo(second);
        // репозиторий должен был вызваться 1 раз
        then(productRepo).should(times(1)).findAll(pageable);
        // в кеше лежит результат под ключом "page-size-sort"
        String key = "0-2-" + pageable.getSort().toString();
        Cache cache = cacheManager.getCache("products");
        @SuppressWarnings("unchecked")
        List<ProductDto> cached = cache.get(key, List.class);
        assertThat(cached).containsExactly(sampleDto);
    }

    @Test
    @DisplayName("работа кэширования при добавлении нового продукта")
    void caching_evictProductsOnAddAndPopulateProduct() {
        ProductDtoRequest req = new ProductDtoRequest("New", 2, BigDecimal.valueOf(20.0));
        Product savedEntity = Product.builder()
                .id(100L)
                .articleId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .name("New").quantity(2).price(BigDecimal.valueOf(20.0)).build();
        ProductDto returnedDto = new ProductDto(
                savedEntity.getArticleId(),
                savedEntity.getName(),
                savedEntity.getQuantity(),
                savedEntity.getPrice()
        );

        given(productMapper.toEntity(any(ProductDto.class))).willReturn(savedEntity);
        given(productRepo.save(savedEntity)).willReturn(savedEntity);
        given(productMapper.toDto(savedEntity)).willReturn(returnedDto);

        // 1) заполняем кэш getAllProducts
        Pageable page = PageRequest.of(0, 10, Sort.by("name").ascending());
        given(productRepo.findAll(page))
                .willReturn(new PageImpl<>(List.of()));

        // первый вызов — кладёт в кэш пустой список
        List<ProductDto> before = productService.getAllProducts(page);
        assertThat(before).isEmpty();

        // вычисляем точный ключ
        String productsKey = page.getPageNumber()
                + "-" + page.getPageSize()
                + "-" + page.getSort().toString();
        // убеждаемся, что кэш не пустой
        assertThat(cacheManager.getCache("products").get(productsKey)).isNotNull();

        // 2) вызываем addProduct → должен инвалидировать "products" и заполнить "product"
        ProductDto actual = productService.addProduct(req);
        assertThat(actual).isEqualTo(returnedDto);

        // 3) кэш "products" под тем же ключом должен исчезнуть
        assertThat(cacheManager.getCache("products").get(productsKey)).isNull();

        // 4) а кэш "product" под ключом articleId должен содержать returnedDto
        Cache.ValueWrapper wrapped = cacheManager.getCache("product")
                .get(savedEntity.getArticleId());
        assertThat(wrapped).isNotNull();
        assertThat(wrapped.get()).isEqualTo(returnedDto);

        // убедиться, что репозиторий сохранялся
        then(productRepo).should().save(savedEntity);
    }

    @Test
    @DisplayName("работа кэширования при удалении продукта")
    void caching_evictCachesOnDelete() {
        cacheManager.getCache("products").put("0-2-" + pageable.getSort(), List.of(sampleDto));
        cacheManager.getCache("product").put(sampleId, sampleDto);

        given(productRepo.findByArticleId(sampleId)).willReturn(Optional.of(sampleEntity));

        productService.deleteProduct(sampleId);

        assertThat(cacheManager.getCache("products").get("0-2-" + pageable.getSort())).isNull();
        assertThat(cacheManager.getCache("product").get(sampleId)).isNull();

        then(productRepo).should().deleteByArticleId(sampleId);
    }

    @Test
    @DisplayName("списание остатков при создании заказа инвалидирует кэши product и products")
    void caching_evictCachesOnChangeQuantityAfterCreateOrder() {
        cacheManager.getCache("products").put("0-2-" + pageable.getSort(), List.of(sampleDto));
        cacheManager.getCache("product").put(sampleId, sampleDto);

        given(stockOperationRepo.existsByOrderUuidAndType(any(UUID.class), any(com.utochkin.shopservice.models.StockOperationType.class)))
                .willReturn(false);
        given(productRepo.findAllByArticleIds(List.of(sampleId))).willReturn(List.of(sampleEntity));
        given(productRepo.deductStock(sampleId, 2)).willReturn(1);

        productService.changeTotalQuantityProductsAfterCreateOrder(UUID.randomUUID(),
                List.of(new OrderRequest(sampleId, 2)));

        assertThat(cacheManager.getCache("products").get("0-2-" + pageable.getSort())).isNull();
        assertThat(cacheManager.getCache("product").get(sampleId)).isNull();

        then(productRepo).should().deductStock(sampleId, 2);
    }

    @Test
    @DisplayName("восстановление остатков при возврате заказа инвалидирует кэши product и products")
    void caching_evictCachesOnChangeQuantityAfterRefundedOrder() {
        cacheManager.getCache("products").put("0-2-" + pageable.getSort(), List.of(sampleDto));
        cacheManager.getCache("product").put(sampleId, sampleDto);

        given(stockOperationRepo.existsByOrderUuidAndType(any(UUID.class), any(com.utochkin.shopservice.models.StockOperationType.class)))
                .willReturn(false);
        given(productRepo.restoreStock(sampleId, 4)).willReturn(1);

        productService.changeTotalQuantityProductsAfterRefundedOrder(UUID.randomUUID(),
                List.of(new OrderRequest(sampleId, 4)));

        assertThat(cacheManager.getCache("products").get("0-2-" + pageable.getSort())).isNull();
        assertThat(cacheManager.getCache("product").get(sampleId)).isNull();

        then(productRepo).should().restoreStock(sampleId, 4);
    }

    @Test
    @DisplayName("работа кэширования при обновлении продукта")
    void caching_putOnUpdateAndEvictProducts() {
        cacheManager.getCache("products").put("0-2-" + pageable.getSort(), List.of(sampleDto));

        given(productRepo.findByArticleId(sampleId)).willReturn(Optional.of(sampleEntity));

        ProductDtoRequest req = new ProductDtoRequest("B", 7, BigDecimal.valueOf(15.0));

        ProductDto updatedDto = new ProductDto(sampleId, "B", 7, BigDecimal.valueOf(15.0));
        given(productMapper.toDto(sampleEntity)).willReturn(updatedDto);

        ProductDto result = productService.updateProduct(sampleId, req);

        assertThat(result).isEqualTo(updatedDto);

        assertThat(cacheManager.getCache("products").get("0-2-" + pageable.getSort())).isNull();

        ProductDto cached = cacheManager.getCache("product").get(sampleId, ProductDto.class);
        assertThat(cached).isEqualTo(updatedDto);

        then(productRepo).should().save(sampleEntity);
    }
}
