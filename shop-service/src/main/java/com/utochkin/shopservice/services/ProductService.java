package com.utochkin.shopservice.services;


import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.dto.ProductDtoRequest;
import com.utochkin.shopservice.exceptions.ProductNotFoundException;
import com.utochkin.shopservice.mappers.ProductMapper;
import com.utochkin.shopservice.models.Product;
import com.utochkin.shopservice.repositories.ProductRepository;
import com.utochkin.shopservice.requests.OrderRequest;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Log4j2
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public Boolean checkOrder(List<OrderRequest> orderRequests) {
        log.info("ProductService: проверка остатков для {}", orderRequests);

        List<UUID> listUuids = orderRequests.stream().map(OrderRequest::getArticleId).toList();

        List<Product> products = productRepository.findAllByArticleIds(listUuids);

        Map<UUID, Integer> productQuantities = products.stream().collect(Collectors.toMap(Product::getArticleId, Product::getQuantity));

        boolean allAvailable  = orderRequests.stream()
                .allMatch(orderRequest -> {
                    Integer availableQuantity = productQuantities.get(orderRequest.getArticleId());
                    return availableQuantity != null && availableQuantity >= orderRequest.getQuantity();
                });

        log.info("ProductService: количество продуктов {} для заказа", allAvailable ? "достаточно" : "недостаточно");

        return allAvailable;

    }

    @Transactional(readOnly = true)
    public Double getSumTotalPriceOrder(List<OrderRequest> orderRequests){
        log.info("ProductService: расчёт суммы для {}", orderRequests);

        List<UUID> listUuids = orderRequests.stream().map(OrderRequest::getArticleId).toList();

        List<Product> products = productRepository.findAllByArticleIds(listUuids);

        Map<UUID, Double> productPrices = products.stream().collect(Collectors.toMap(Product::getArticleId, Product::getPrice));

        double rawSum = orderRequests.stream()
                .mapToDouble(orderRequest -> {
                    Double price = productPrices.get(orderRequest.getArticleId());
                    if (price == null) {
                        throw new ProductNotFoundException("Продукт с articleId " + orderRequest.getArticleId() + " не найден");
                    }
                    return price * orderRequest.getQuantity();
                })
                .sum();

        BigDecimal rounded = BigDecimal.valueOf(rawSum).setScale(2, RoundingMode.HALF_UP);

        log.info("ProductService: сумма заказа {}", rounded);

        return rounded.doubleValue();
    }

    @Transactional
    public void changeTotalQuantityProductsAfterCreateOrder(List<OrderRequest> orderRequests) {
        log.info("ProductService: уменьшаем остатки продуктов в бд после создания заказа {}", orderRequests);

        List<UUID> listUuids = orderRequests.stream().map(OrderRequest::getArticleId).toList();

        List<Product> products = productRepository.findAllByArticleIds(listUuids);

        Map<UUID, Product> productMap = products.stream().collect(Collectors.toMap(Product::getArticleId, product -> product));

        for (OrderRequest orderRequest : orderRequests) {
            Product product = productMap.get(orderRequest.getArticleId());

            if (product == null) {
                throw new ProductNotFoundException("Продукт с articleId " + orderRequest.getArticleId() + " не найден");
            }

            int remainingQuantity = product.getQuantity() - orderRequest.getQuantity();
            if (remainingQuantity < 0) {
                throw new ProductNotFoundException("Недостаточно товара с articleId " + orderRequest.getArticleId());
            }

            product.setQuantity(remainingQuantity);
        }

        productRepository.saveAll(products);

        log.info("ProductService: остатки обновлены");
    }

    public void changeTotalQuantityProductsAfterRefundedOrder(List<OrderRequest> orderRequests) {
        log.info("ProductService: восстанавливаем остатки продуктов в бд после возврата {}", orderRequests);

        List<UUID> listUuids = orderRequests.stream().map(OrderRequest::getArticleId).toList();

        List<Product> products = productRepository.findAllByArticleIds(listUuids);

        Map<UUID, Product> productMap = products.stream().collect(Collectors.toMap(Product::getArticleId, product -> product));

        for (OrderRequest orderRequest : orderRequests) {
            Product product = productMap.get(orderRequest.getArticleId());

            if (product == null) {
                throw new ProductNotFoundException("Продукт с articleId " + orderRequest.getArticleId() + " не найден");
            }

            int updatedQuantity = product.getQuantity() + orderRequest.getQuantity();
            product.setQuantity(updatedQuantity);
        }

        productRepository.saveAll(products);

        log.info("ProductService: остатки восстановлены");
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    public List<ProductDto> getAllProducts(Pageable pageable) {
        log.info("ProductService: получение всех продуктов {}", pageable);
        Page<Product> productsPage = productRepository.findAll(pageable);
        return productMapper.toListDto(productsPage.getContent());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product", key = "#articleId")
    public ProductDto getProduct(UUID articleId) {
        log.info("ProductService: получение продукта {}", articleId);
        Product product = productRepository.findByArticleId(articleId).orElseThrow(() -> new ProductNotFoundException("Продукт с articleId " + articleId + " не найден"));
        return productMapper.toDto(product);
    }


    @Transactional
    @Caching(
            put = { @CachePut(value = "product", key = "#result.articleId") },
            evict = { @CacheEvict(value = "products", allEntries = true) }
    )
    public ProductDto addProduct(ProductDtoRequest productDtoRequest) {
        log.info("ProductService: добавление продукта {}", productDtoRequest);
        ProductDto productDto = new ProductDto(UUID.randomUUID(), productDtoRequest.getName(), productDtoRequest.getQuantity(), productDtoRequest.getPrice());
        Product product = productMapper.toEntity(productDto);
        productRepository.save(product);
        return productMapper.toDto(product);
    }

    @Transactional
    @CacheEvict(value = {"products", "product"}, allEntries = true)
    public void deleteProduct(UUID articleId) {
        log.info("ProductService: удаление продукта {}", articleId);
        Optional <Product> productByArticleId = productRepository.findByArticleId(articleId);
        if(productByArticleId.isPresent()){
            productRepository.deleteByArticleId(articleId);
        } else {
            throw new ProductNotFoundException("Not found this product!");
        }

    }

    @Transactional
    @Caching(
            put = { @CachePut(value = "product", key = "#articleId") },
            evict = { @CacheEvict(value = "products", allEntries = true) }
    )
    public ProductDto updateProduct(UUID articleId, ProductDtoRequest productDtoRequest) {
        log.info("ProductService: обновление продукта {} -> {}", articleId, productDtoRequest);
        Optional <Product> productByArticleId = productRepository.findByArticleId(articleId);
        if(productByArticleId.isPresent()){
            Product product = productByArticleId.get();
            product.setName(productDtoRequest.getName());
            product.setQuantity(productDtoRequest.getQuantity());
            product.setPrice(productDtoRequest.getPrice());

            productRepository.save(product);
            return productMapper.toDto(product);
        } else {
            throw new ProductNotFoundException("Not found this product!");
        }
    }
}

