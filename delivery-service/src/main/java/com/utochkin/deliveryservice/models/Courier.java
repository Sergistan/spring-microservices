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
 * Курьер — зеркало пользователя Keycloak с client-role COURIER (синхронизируется
 * CourierSyncJob из Admin REST). Статус занятости — собственная правда delivery-service,
 * его Keycloak не знает: AVAILABLE (можно назначать доставки) / BUSY.
 */
@Entity
@Table(name = "couriers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub пользователя Keycloak (= claim sub его JWT). */
    @Column(name = "sub_id", nullable = false, unique = true)
    private UUID subId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email")
    private String email;

    /** AVAILABLE — свободен, BUSY — выполняет доставку (меняется атомарно через CourierRepository.tryAssign). */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CourierStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
