package com.utochkin.shopservice.services;


import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.exceptions.ProductNotFoundException;
import com.utochkin.shopservice.mappers.ProductMapper;
import com.utochkin.shopservice.models.Product;
import com.utochkin.shopservice.repositories.ProductRepository;
import com.utochkin.shopservice.request.OrderRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;


    @Transactional(readOnly = true)
    public Boolean checkOrder(List<OrderRequest> orderRequests) {
        List<UUID> listUuids = orderRequests.stream().map(OrderRequest::getArticleId).toList();

        List<Product> products = productRepository.findAllByArticleIds(listUuids);

        Map<UUID, Integer> productQuantities = products.stream().collect(Collectors.toMap(Product::getArticleId, Product::getQuantity));

        return orderRequests.stream()
                .allMatch(orderRequest -> {
                    Integer availableQuantity = productQuantities.get(orderRequest.getArticleId());
                    return availableQuantity != null && availableQuantity >= orderRequest.getQuantity();
                });

    }

    @Transactional(readOnly = true)
    public Double getSumTotalPriceOrder(List<OrderRequest> orderRequests) {
        List<UUID> listUuids = orderRequests.stream().map(OrderRequest::getArticleId).toList();

        List<Product> products = productRepository.findAllByArticleIds(listUuids);

        Map<UUID, Double> productPrices = products.stream().collect(Collectors.toMap(Product::getArticleId, Product::getPrice));

        return orderRequests.stream()
                .mapToDouble(orderRequest -> {
                    Double price = productPrices.get(orderRequest.getArticleId());
                    if (price == null) {
                        throw new ProductNotFoundException("Продукт с articleId " + orderRequest.getArticleId() + " не найден");
                    }
                    return price * orderRequest.getQuantity();
                })
                .sum();
    }

    @Transactional
    public void changeTotalQuantityProductsAfterCreateOrder(List<OrderRequest> orderRequests) {
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
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getAllProducts() {
        List<Product> products = productRepository.findAll();
        return productMapper.toListDto(products);
    }

    @Transactional(readOnly = true)
    public ProductDto getProduct(UUID articleId) {
        Product product = productRepository.findByArticleId(articleId).orElseThrow(() -> new ProductNotFoundException("Продукт с articleId " + articleId + " не найден"));
        return productMapper.toDto(product);
    }


    @Transactional
    public ProductDto addProduct(ProductDto productDtoRequest) {
        ProductDto productDto = new ProductDto(UUID.randomUUID(), productDtoRequest.name(), productDtoRequest.quantity(), productDtoRequest.price());
        Product product = productMapper.toEntity(productDto);
        productRepository.save(product);
        return productMapper.toDto(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional
    public ProductDto updateProduct(ProductDto productDto) {
        Product product = productMapper.toEntity(productDto);
        productRepository.save(product);
        return productMapper.toDto(product);
    }
}

