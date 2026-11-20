package com.utochkin.deliveryservice.services;

import com.utochkin.deliveryservice.keycloak.KeycloakAdminClient;
import com.utochkin.deliveryservice.keycloak.KeycloakUser;
import com.utochkin.deliveryservice.models.Courier;
import com.utochkin.deliveryservice.models.CourierStatus;
import com.utochkin.deliveryservice.models.DeliveryStatus;
import com.utochkin.deliveryservice.repositories.CourierRepository;
import com.utochkin.deliveryservice.repositories.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Синхронизация справочника курьеров с Keycloak (каждые delivery.courier-sync.fixed-delay,
 * по умолчанию 5 минут): список пользователей с client-role COURIER из Admin REST
 * (клиент delivery-service-client, роли realm-management view-users/view-clients — только чтение).
 *
 * Правила upsert по sub_id: новый курьер → AVAILABLE; существующий → обновить ФИО/email,
 * статус занятости НЕ трогаем (BUSY/AVAILABLE — собственная правда delivery-service).
 * Осиротевшие (удалённые из Keycloak): удаляем, только если свободен и не имеет АКТИВНЫХ
 * доставок (исторические DELIVERED/CANCELLED не мешают) — иначе пусть дозавершит активную
 * доставку (назначаться повторно он уже не сможет: tryAssign выбирает только AVAILABLE из справочника).
 * Сбой Keycloak не валит джобу — следующая итерация догонит.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class CourierSyncJob {

    private final KeycloakAdminClient keycloakAdminClient;
    private final CourierRepository courierRepository;
    private final DeliveryRepository deliveryRepository;

    @Scheduled(fixedDelayString = "${delivery.courier-sync.fixed-delay:300000}")
    public void syncCouriers() {
        try {
            String clientUuid = keycloakAdminClient.getClientUuid();
            List<KeycloakUser> keycloakCouriers = keycloakAdminClient.getUsersWithClientRole(clientUuid, "COURIER");

            upsert(keycloakCouriers);
            removeOrphans(keycloakCouriers);
        } catch (Exception e) {
            log.warn("CourierSyncJob: синхронизация курьеров не удалась ({}), следующая итерация догонит", e.getMessage());
        }
    }

    private void upsert(List<KeycloakUser> keycloakCouriers) {
        int created = 0;
        for (KeycloakUser kc : keycloakCouriers) {
            UUID subId = UUID.fromString(kc.id());
            Courier courier = courierRepository.findBySubId(subId).orElseGet(() -> Courier.builder()
                    .subId(subId)
                    .status(CourierStatus.AVAILABLE)
                    .createdAt(LocalDateTime.now())
                    .build());
            boolean isNew = courier.getId() == null;
            courier.setUsername(kc.username());
            courier.setFirstName(kc.firstName());
            courier.setLastName(kc.lastName());
            courier.setEmail(kc.email());
            courier.setUpdatedAt(LocalDateTime.now());
            courierRepository.save(courier);
            if (isNew) {
                created++;
            }
        }
        log.info("CourierSyncJob: синхронизировано {} курьеров из Keycloak (новых: {})", keycloakCouriers.size(), created);
    }

    private void removeOrphans(List<KeycloakUser> keycloakCouriers) {
        Map<UUID, KeycloakUser> bySub = keycloakCouriers.stream()
                .collect(Collectors.toMap(kc -> UUID.fromString(kc.id()), Function.identity()));
        // Активные доставки: исторические (DELIVERED/CANCELLED) удалению курьера не мешают
        List<DeliveryStatus> activeStatuses = List.of(
                DeliveryStatus.SEARCHING_COURIER, DeliveryStatus.ASSIGNED, DeliveryStatus.IN_DELIVERY);
        for (Courier existing : courierRepository.findAll()) {
            if (bySub.containsKey(existing.getSubId())) {
                continue;
            }
            boolean hasActiveDeliveries =
                    !deliveryRepository.findByCourierIdAndStatusIn(existing.getId(), activeStatuses).isEmpty();
            if (existing.getStatus() == CourierStatus.AVAILABLE && !hasActiveDeliveries) {
                courierRepository.delete(existing);
                log.info("CourierSyncJob: курьер {} (subId={}) удалён из Keycloak и без активных доставок — убран из справочника",
                        existing.getUsername(), existing.getSubId());
            } else {
                log.warn("CourierSyncJob: курьер {} (subId={}) отсутствует в Keycloak, но занят/имеет активные доставки — оставлен до завершения",
                        existing.getUsername(), existing.getSubId());
            }
        }
    }
}
