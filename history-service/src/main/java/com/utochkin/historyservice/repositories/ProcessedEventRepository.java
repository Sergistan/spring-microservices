package com.utochkin.historyservice.repositories;

import com.utochkin.historyservice.models.ProcessedEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
/**
 * Маркеры обработанных eventId (unique index) — дедупликация at-least-once доставки Kafka.
 */
public interface ProcessedEventRepository extends MongoRepository<ProcessedEvent, String> {
}
