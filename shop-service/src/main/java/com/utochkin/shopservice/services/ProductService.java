package com.utochkin.shopservice.services;


import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.exceptions.ProductNotFoundException;
import com.utochkin.shopservice.mappers.ProductMapper;
import com.utochkin.shopservice.models.Product;
import com.utochkin.shopservice.repositories.ProductRepository;
import com.utochkin.shopservice.requests.OrderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    public Double getSumTotalPriceOrder(List<OrderRequest> orderRequests){
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

    public void changeTotalQuantityProductsAfterRefundedOrder(List<OrderRequest> orderRequests) {
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
        ProductDto productDto = new ProductDto(UUID.randomUUID(), productDtoRequest.getName(), productDtoRequest.getQuantity(), productDtoRequest.getPrice());
        Product product = productMapper.toEntity(productDto);
        productRepository.save(product);
        return productMapper.toDto(product);
    }

    @Transactional
    public void deleteProduct(UUID articleId) {
        Optional <Product> productByArticleId = productRepository.findByArticleId(articleId);
        if(productByArticleId.isPresent()){
            productRepository.deleteByArticleId(articleId);
        } else {
            throw new ProductNotFoundException("Not found this product!");
        }

    }

    @Transactional
    public ProductDto updateProduct(UUID articleId, ProductDto productDto) {
        Optional <Product> productByArticleId = productRepository.findByArticleId(articleId);
        if(productByArticleId.isPresent()){
            Product product = productByArticleId.get();
            product.setName(productDto.getName());
            product.setQuantity(productDto.getQuantity());
            product.setPrice(productDto.getPrice());

            productRepository.save(product);
            return productMapper.toDto(product);
        } else {
            throw new ProductNotFoundException("Not found this product!");
        }
    }
}

