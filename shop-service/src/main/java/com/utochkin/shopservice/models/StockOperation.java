package com.utochkin.shopservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Маркер идемпотентности изменения остатков: UNIQUE(order_uuid, type) защищает от повторного списания/восстановления. */
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Builder
@Table(name = "stock_operations", uniqueConstraints = @UniqueConstraint(name = "ux_stock_operations", columnNames = {"order_uuid", "type"}))
public class StockOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_uuid", nullable = false)
    private UUID orderUuid;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private StockOperationType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
