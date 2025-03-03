package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;


public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o.totalAmount FROM Order o WHERE o.orderUuid = :orderUuid")
    Double findTotalAmountByOrderUuid(@Param("orderUuid") UUID orderUuid);

    Optional<Order> findByOrderUuid(UUID orderUuid);
}
