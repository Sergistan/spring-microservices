package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.ProductInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductInfoRepository extends JpaRepository<ProductInfo, Long> {
}
