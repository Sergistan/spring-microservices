package com.utochkin.deliveryservice.repositories;

import com.utochkin.deliveryservice.models.Courier;
import com.utochkin.deliveryservice.models.CourierStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Курьеры. tryAssign/release — атомарные CAS-переходы занятости: два конкурирующих
 * потока назначения не займут одного курьера (проигравший получает 0 строк).
 */
public interface CourierRepository extends JpaRepository<Courier, Long> {

    /** Свободные курьеры в стабильном порядке (кто раньше зарегистрировался — тот первый в очереди). */
    List<Courier> findByStatusOrderByIdAsc(CourierStatus status);

    Optional<Courier> findBySubId(UUID subId);

    List<Courier> findAll();

    /** CAS: занять свободного курьера. 1 = мы заняли, 0 = конкурент успел раньше. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Courier c SET c.status = 'BUSY', c.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE c.id = :id AND c.status = 'AVAILABLE'")
    int tryAssign(@Param("id") Long id);

    /** CAS: освободить занятого курьера (после DELIVERED/CANCELLED). Идемпотентен при статусе AVAILABLE. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Courier c SET c.status = 'AVAILABLE', c.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE c.id = :id AND c.status = 'BUSY'")
    int release(@Param("id") Long id);
}
