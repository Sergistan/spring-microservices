package com.utochkin.deliveryservice.repositories;

import com.utochkin.deliveryservice.models.Delivery;
import com.utochkin.deliveryservice.models.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Доставки. transitionStatus/assignCourier — CAS-переходы по образцу OrderRepository:
 * атомарный условный UPDATE, 0 строк = конкурент уже изменил состояние (или доставки нет).
 */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderUuid(UUID orderUuid);

    boolean existsByOrderUuid(UUID orderUuid);

    /** Кандидаты на старт доставки: T+5 минут с момента оплаты истёк, стадия ещё не финальная (выборка DeliveryProgressJob). */
    List<Delivery> findByStatusInAndPaidAtBefore(Collection<DeliveryStatus> statuses, LocalDateTime paidAtBefore);

    /** Доставки в заданной стадии, «зависшие» дольше порога (повторное назначение курьера / завершение). */
    List<Delivery> findByStatusAndUpdatedAtBefore(DeliveryStatus status, LocalDateTime updatedAtBefore);

    /** Все доставки курьера (исторические включительно). */
    List<Delivery> findByCourierId(Long courierId);

    /** Активные доставки курьера (SEARCHING_COURIER/ASSIGNED/IN_DELIVERY) — исторические DELIVERED/CANCELLED не считаются. */
    List<Delivery> findByCourierIdAndStatusIn(Long courierId, Collection<DeliveryStatus> statuses);

    /** CAS-перевод статуса: UPDATE ... WHERE order_uuid = ? AND status IN (allowed). 1 = перевели, 0 = конкурент успел. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Delivery d SET d.status = :target, d.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE d.orderUuid = :orderUuid AND d.status IN :allowed")
    int transitionStatus(@Param("orderUuid") UUID orderUuid,
                         @Param("allowed") Collection<DeliveryStatus> allowed,
                         @Param("target") DeliveryStatus target);

    /** CAS: назначить курьера доставке в SEARCHING_COURIER. 0 = доставка уже отменена/занята (курьера надо освободить). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Delivery d SET d.courierId = :courierId, d.status = 'ASSIGNED', d.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE d.orderUuid = :orderUuid AND d.status = 'SEARCHING_COURIER'")
    int assignCourier(@Param("orderUuid") UUID orderUuid, @Param("courierId") Long courierId);
}
