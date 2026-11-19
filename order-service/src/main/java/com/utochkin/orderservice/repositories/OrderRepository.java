package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.models.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


/**
 * Заказы. Ключевой метод — transitionStatus (CAS): атомарный перевод статуса только
 * из разрешённого списка; конкурент, пришедший вторым, получает 0 строк.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Сумма заказа (для запроса платежа). */
    @Query("SELECT o.totalAmount FROM Order o WHERE o.orderUuid = :orderUuid")
    BigDecimal findTotalAmountByOrderUuid(@Param("orderUuid") UUID orderUuid);

    /** Заказ по бизнес-ключу orderUuid (приходит из API). */
    Optional<Order> findByOrderUuid(UUID orderUuid);

    /** До 50 заказов в заданных статусах, не обновлявшихся раньше порога (выборка ReconciliationJob). */
    List<Order> findTop50ByOrderStatusInAndUpdatedAtBefore(Collection<Status> orderStatuses, LocalDateTime updatedAtBefore);

    /**
     * CAS-перевод статуса: UPDATE ... WHERE orderUuid = ? AND orderStatus IN (allowedStatuses).
     * Возврат 1 = мы перевели, 0 = конкурент уже изменил статус (или заказа нет).
     * clearAutomatically/flushAutomatically — сброс контекста после UPDATE, чтобы далее
     * читать свежие данные первой сущности.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.orderStatus = :targetStatus, o.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE o.orderUuid = :orderUuid AND o.orderStatus IN :allowedStatuses")
    int transitionStatus(@Param("orderUuid") UUID orderUuid,
                         @Param("allowedStatuses") Collection<Status> allowedStatuses,
                         @Param("targetStatus") Status targetStatus);
}
