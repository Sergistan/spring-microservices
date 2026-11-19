package com.utochkin.shopservice.repositories;

import com.utochkin.shopservice.models.StockOperation;
import com.utochkin.shopservice.models.StockOperationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
/**
 * Маркеры операций остатков по заказам (DEDUCT/RESTORE) — идемпотентность списания/возврата.
 */
public interface StockOperationRepository extends JpaRepository<StockOperation, Long> {

    /** Выполнялась ли операция остатков по заказу (маркер идемпотентности DEDUCT/RESTORE). */
    boolean existsByOrderUuidAndType(UUID orderUuid, StockOperationType type);
}
