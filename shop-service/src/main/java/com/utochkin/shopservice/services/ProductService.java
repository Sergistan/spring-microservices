package com.utochkin.shopservice.services;


import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.web.ProductDtoRequest;
import com.utochkin.shopservice.exceptions.InsufficientStockException;
import com.utochkin.shopservice.exceptions.ProductNotFoundException;
import com.utochkin.shopservice.mappers.ProductMapper;
import com.utochkin.shopservice.models.Product;
import com.utochkin.shopservice.models.StockOperation;
import com.utochkin.shopservice.models.StockOperationType;
import com.utochkin.shopservice.repositories.ProductRepository;
import com.utochkin.shopservice.repositories.StockOperationRepository;
import com.utochkin.shopservice.feign.OrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * Товарный каталог shop-service: чтение (с Redis-кэшем), CRUD администратора и — главное —
 * атомарное изменение остатков для заказов:
 *  - deductStock/restoreStock — одна строка UPDATE ... WHERE quantity >= :quantity (CHECK в БД):
 *    списание не уводит остаток в минус, а результат 0 строк = товара не хватило;
 *  - после любого изменения остатков кэш products/product инвалидируется (@CacheEvict);
 *  - чтение каталога кэшируется с sync=true (одна нить греет кэш, остальные ждут).
 * Вызывается и REST-ом (админ), и order-service через Feign (checkOrder/списание/возврат).
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class ProductService {

    private final ProductRepository productRepository;
    private final StockOperationRepository stockOperationRepository;
    private final ProductMapper productMapper;

    /** Загрузка сущностей товаров по списку articleId (одним запросом IN). */
    private List<Product> fetchProducts(List<OrderRequest> orderRequests) {
        List<UUID> ids = orderRequests.stream()
                .map(OrderRequest::getArticleId)
                .toList();
        return productRepository.findAllByArticleIds(ids);
    }

    /** Проверка перед созданием заказа: у каждого артикула есть товар в нужном количестве. */
    @Transactional(readOnly = true)
    public Boolean checkOrder(List<OrderRequest> orderRequests) {
        log.info("ProductService: проверка остатков для {}", orderRequests);

        List<Product> products = fetchProducts(orderRequests);

        Map<UUID, Integer> productQuantities = products.stream().collect(Collectors.toMap(Product::getArticleId, Product::getQuantity));

        boolean allAvailable = orderRequests.stream()
                .allMatch(orderRequest -> {
                    Integer availableQuantity = productQuantities.get(orderRequest.getArticleId());
                    return availableQuantity != null && availableQuantity >= orderRequest.getQuantity();
                });

        log.info("ProductService: количество продуктов {} для заказа", allAvailable ? "достаточно" : "недостаточно");

        return allAvailable;

    }

    /** Сумма заказа: цена x количество по позициям, scale=2 HALF_UP; неизвестный артикул -> 404. */
    @Transactional(readOnly = true)
    public BigDecimal getSumTotalPriceOrder(List<OrderRequest> orderRequests) {
        log.info("ProductService: расчёт суммы для {}", orderRequests);

        List<Product> products = fetchProducts(orderRequests);

        Map<UUID, BigDecimal> productPrices = products.stream().collect(Collectors.toMap(Product::getArticleId, Product::getPrice));

        BigDecimal sum = orderRequests.stream()
                .map(orderRequest -> {
                    BigDecimal price = productPrices.get(orderRequest.getArticleId());
                    if (price == null) {
                        throw new ProductNotFoundException("Продукт с articleId " + orderRequest.getArticleId() + " не найден");
                    }
                    return price.multiply(BigDecimal.valueOf(orderRequest.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rounded = sum.setScale(2, RoundingMode.HALF_UP);

        log.info("ProductService: сумма заказа {}", rounded);

        return rounded;
    }

    /**
     * Списание остатков после создания заказа. Идемпотентность ДВУХ уровней:
     *  1) маркер stock_operations (orderUuid + DEDUCT) — повторный вызов с тем же заказом пропускается;
     *  2) атомарный UPDATE ... WHERE quantity >= n — списание не уводит в минус
     *     (0 строк = InsufficientStockException -> вся транзакция откатывается).
     * После успеха кэш каталога инвалидируется (@CacheEvict).
     */
    @Transactional
    @CacheEvict(value = {"products", "product"}, allEntries = true)
    public void changeTotalQuantityProductsAfterCreateOrder(UUID orderUuid, List<OrderRequest> orderRequests) {
        log.info("ProductService: уменьшаем остатки продуктов в бд после создания заказа {} {}", orderUuid, orderRequests);

        if (stockOperationRepository.existsByOrderUuidAndType(orderUuid, StockOperationType.DEDUCT)) {
            log.info("ProductService: списание для заказа {} уже выполнялось — пропускаем (идемпотентный повтор)", orderUuid);
            return;
        }

        Map<UUID, Boolean> existingProducts = fetchProducts(orderRequests).stream()
                .collect(Collectors.toMap(Product::getArticleId, product -> true));

        for (OrderRequest orderRequest : orderRequests) {
            if (!existingProducts.containsKey(orderRequest.getArticleId())) {
                throw new ProductNotFoundException("Продукт с articleId " + orderRequest.getArticleId() + " не найден");
            }

            int updated = productRepository.deductStock(orderRequest.getArticleId(), orderRequest.getQuantity());
            if (updated == 0) {
                throw new InsufficientStockException("Недостаточно товара с articleId " + orderRequest.getArticleId());
            }
        }

        stockOperationRepository.save(StockOperation.builder()
                .orderUuid(orderUuid)
                .type(StockOperationType.DEDUCT)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("ProductService: остатки обновлены");
    }

    /**
     * Возврат остатков после отмены заказа (идемпотентно по маркеру orderUuid + RESTORE).
     * Если товар успели удалить — восстановление для него мягко пропускается (warn), остальные возвращаются.
     */
    @Transactional
    @CacheEvict(value = {"products", "product"}, allEntries = true)
    public void changeTotalQuantityProductsAfterRefundedOrder(UUID orderUuid, List<OrderRequest> orderRequests) {
        log.info("ProductService: восстанавливаем остатки продуктов в бд после возврата заказа {} {}", orderUuid, orderRequests);

        if (stockOperationRepository.existsByOrderUuidAndType(orderUuid, StockOperationType.RESTORE)) {
            log.info("ProductService: восстановление для заказа {} уже выполнялось — пропускаем (идемпотентный повтор)", orderUuid);
            return;
        }

        for (OrderRequest orderRequest : orderRequests) {
            int updated = productRepository.restoreStock(orderRequest.getArticleId(), orderRequest.getQuantity());
            if (updated == 0) {
                log.warn("ProductService: товар с articleId {} больше не существует — восстановление остатка для него пропущено",
                        orderRequest.getArticleId());
            }
        }

        stockOperationRepository.save(StockOperation.builder()
                .orderUuid(orderUuid)
                .type(StockOperationType.RESTORE)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("ProductService: остатки восстановлены");
    }

    /** Каталог постранично (Redis-кэш с ключом пагинации; sync=true — одну нить на прогрев). */
    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()", sync = true)
    public List<ProductDto> getAllProducts(Pageable pageable) {
        log.info("ProductService: получение всех продуктов {}", pageable);
        Page<Product> productsPage = productRepository.findAll(pageable);
        return productMapper.toListDto(productsPage.getContent());
    }

    /** Карточка товара по articleId (кэш product, sync=true; 404 если не найден). */
    @Transactional(readOnly = true)
    @Cacheable(value = "product", key = "#articleId", sync = true)
    public ProductDto getProduct(UUID articleId) {
        log.info("ProductService: получение продукта {}", articleId);
        Product product = productRepository.findByArticleId(articleId).orElseThrow(() -> new ProductNotFoundException("Продукт с articleId " + articleId + " не найден"));
        return productMapper.toDto(product);
    }


    /** Создание товара (роль ADMIN): новый articleId; кэш карточки пополняется, список инвалидируется. */
    @Transactional
    @Caching(
            put = {@CachePut(value = "product", key = "#result.articleId")},
            evict = {@CacheEvict(value = "products", allEntries = true)}
    )
    public ProductDto addProduct(ProductDtoRequest productDtoRequest) {
        log.info("ProductService: добавление продукта {}", productDtoRequest);
        ProductDto productDto = new ProductDto(UUID.randomUUID(), productDtoRequest.getName(), productDtoRequest.getQuantity(), productDtoRequest.getPrice());
        Product product = productMapper.toEntity(productDto);
        productRepository.save(product);
        return productMapper.toDto(product);
    }

    /** Удаление товара (роль ADMIN); весь кэш каталога инвалидируется. */
    @Transactional
    @CacheEvict(value = {"products", "product"}, allEntries = true)
    public void deleteProduct(UUID articleId) {
        log.info("ProductService: удаление продукта {}", articleId);
        Optional<Product> productByArticleId = productRepository.findByArticleId(articleId);
        if (productByArticleId.isPresent()) {
            productRepository.deleteByArticleId(articleId);
        } else {
            throw new ProductNotFoundException("Продукт не найден!");
        }

    }

    /** Полное обновление карточки (роль ADMIN): кэш по articleId заменяется, список инвалидируется. */
    @Transactional
    @Caching(
            put = {@CachePut(value = "product", key = "#articleId")},
            evict = {@CacheEvict(value = "products", allEntries = true)}
    )
    public ProductDto updateProduct(UUID articleId, ProductDtoRequest productDtoRequest) {
        log.info("ProductService: обновление продукта {} -> {}", articleId, productDtoRequest);
        Optional<Product> productByArticleId = productRepository.findByArticleId(articleId);
        if (productByArticleId.isPresent()) {
            Product product = productByArticleId.get();
            product.setName(productDtoRequest.getName());
            product.setQuantity(productDtoRequest.getQuantity());
            product.setPrice(productDtoRequest.getPrice());

            productRepository.save(product);
            return productMapper.toDto(product);
        } else {
            throw new ProductNotFoundException("Продукт не найден!");
        }
    }
}

