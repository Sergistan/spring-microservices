package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
/**
 * Таблица outbox: события заказов, ожидающие публикации в Kafka (см. OutboxService/OutboxRelay).
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Пачка до 100 неотправленных событий с блокировкой строк FOR UPDATE SKIP LOCKED:
     * параллельные инстансы/потоки забирают НЕПЕРЕСЕКАЮЩИЕСЯ пачки (занятые строки пропускаются).
     */
    @Query(value = "SELECT * FROM outbox_events ORDER BY id ASC LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockBatch();
}
