package com.utochkin.orderservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Запись transactional outbox: событие фиксируется в транзакции бизнес-операции,
 * OutboxRelay публикует его в topic-orders (FOR UPDATE SKIP LOCKED) и удаляет.
 */
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Builder
@Table(name = "outbox_events", uniqueConstraints = @UniqueConstraint(name = "ux_outbox_event_id", columnNames = "event_id"))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "order_uuid", nullable = false)
    private UUID orderUuid;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
