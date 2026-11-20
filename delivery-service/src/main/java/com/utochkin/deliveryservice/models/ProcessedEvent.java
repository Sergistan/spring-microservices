package com.utochkin.deliveryservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Маркер обработанного Kafka-события (event_id из OrderDtoForKafka, PK):
 * вставка маркера и создание/отмена доставки — в одной транзакции;
 * DataIntegrityViolationException = повторная доставка at-least-once → просто ack.
 * MongoDB-аналог — processed_events в history-service (та же механика, другая СУБД).
 */
@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
