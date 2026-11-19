package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.ProductInfo;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Позиции заказов (articleId + количество), каскадно привязаны к Order.
 */
public interface ProductInfoRepository extends JpaRepository<ProductInfo, Long> {
}
