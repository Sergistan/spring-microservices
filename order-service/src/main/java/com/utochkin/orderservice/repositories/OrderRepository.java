package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.models.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;


public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o.totalAmount FROM Order o WHERE o.orderUuid = :orderUuid")
    BigDecimal findTotalAmountByOrderUuid(@Param("orderUuid") UUID orderUuid);

    Optional<Order> findByOrderUuid(UUID orderUuid);

    @Modifying
    @Query("UPDATE Order o SET o.orderStatus = :targetStatus, o.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE o.orderUuid = :orderUuid AND o.orderStatus IN :allowedStatuses")
    int transitionStatus(@Param("orderUuid") UUID orderUuid,
                         @Param("allowedStatuses") Collection<Status> allowedStatuses,
                         @Param("targetStatus") Status targetStatus);
}
