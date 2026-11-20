package com.utochkin.deliveryservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Доставка оплаченного заказа. Создаётся консьюмером по событию SUCCESS (topic-orders),
 * проходит стадии конвейера (все переходы — CAS через DeliveryRepository.transitionStatus):
 * SEARCHING_COURIER → ASSIGNED → IN_DELIVERY → DELIVERED; любая до IN_DELIVERY → CANCELLED
 * (по событию REFUNDED или 409 от order-service — возврат успел раньше T+5мин).
 */
@Entity
@Table(name = "deliveries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Бизнес-ключ = order_uuid заказа (UNIQUE — защита от дублей событий). */
    @Column(name = "order_uuid", nullable = false, unique = true)
    private UUID orderUuid;

    /** Назначенный курьер (null, пока доставка в SEARCHING_COURIER). */
    @Column(name = "courier_id")
    private Long courierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeliveryStatus status;

    /** Момент оплаты заказа (из SUCCESS-события) — от него отсчитывается T+5 минут до старта доставки. */
    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    /** Момент вручения заказа покупателю. */
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
