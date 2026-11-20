package com.utochkin.deliveryservice.repositories;

import com.utochkin.deliveryservice.models.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Маркеры обработанных Kafka-событий (дедупликация at-least-once по event_id). */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
