package com.utochkin.shopservice.repositories;

import com.utochkin.shopservice.models.StockOperation;
import com.utochkin.shopservice.models.StockOperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockOperationRepository extends JpaRepository<StockOperation, Long> {

    boolean existsByOrderUuidAndType(UUID orderUuid, StockOperationType type);
}
