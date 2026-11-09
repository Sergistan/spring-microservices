package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = "SELECT * FROM outbox_events ORDER BY id ASC LIMIT 100 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockBatch();
}
