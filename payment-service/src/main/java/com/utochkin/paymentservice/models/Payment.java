package com.utochkin.paymentservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Платёж/возврат: UNIQUE(order_uuid, type) — ключ идемпотентности против двойного списания/возврата. */
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Builder
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uq_payments_order_type", columnNames = {"order_uuid", "type"}))
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_uuid", nullable = false)
    private UUID orderUuid;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
