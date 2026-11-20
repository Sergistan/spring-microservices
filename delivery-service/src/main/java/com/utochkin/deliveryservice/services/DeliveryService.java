package com.utochkin.deliveryservice.services;

import com.utochkin.deliveryservice.dto.OrderDtoForKafka;
import com.utochkin.deliveryservice.models.*;
import com.utochkin.deliveryservice.repositories.CourierRepository;
import com.utochkin.deliveryservice.repositories.DeliveryRepository;
import com.utochkin.deliveryservice.repositories.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ядро доставки: обработка событий заказа, назначение курьеров и CAS-переходы стадий.
 *
 * Идемпотентность двух уровней (события приходят at-least-once):
 *  1) маркер processed_events (UNIQUE event_id) — вставка и действие в одной транзакции,
 *     DataIntegrityViolationException = повтор → ничего не делаем;
 *  2) UNIQUE(order_uuid) в deliveries + existsByOrderUuid — повторный SUCCESS без эффекта.
 *
 * Назначение курьера — двойной CAS: сначала атомарно занимаем курьера
 * (CourierRepository.tryAssign, 0 строк = конкурент успел — пробуем следующего),
 * затем атомарно вешаем его на доставку (assignCourier из SEARCHING_COURIER;
 * 0 строк = доставка уже отменена — курьера тут же освобождаем).
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final CourierRepository courierRepository;
    private final ProcessedEventRepository processedEventRepository;

    /**
     * Обработка события topic-orders (вызывается DeliveryConsumer):
     * SUCCESS → создать доставку и попытаться назначить курьера;
     * REFUNDED → отменить доставку и освободить курьера (если ещё можно);
     * прочие статусы — игнор. Всё в одной транзакции с маркером event_id.
     */
    @Transactional
    public void processEvent(OrderDtoForKafka dto) {
        UUID eventId = dto.getEventId();
        if (eventId != null) {
            try {
                processedEventRepository.saveAndFlush(new ProcessedEvent(eventId, LocalDateTime.now()));
            } catch (DataIntegrityViolationException e) {
                log.info("DeliveryService: событие {} уже обработано (дубль at-least-once) — пропускаем", eventId);
                return;
            }
        }
        switch (dto.getOrderStatus()) {
            case SUCCESS -> createDelivery(dto.getOrderUuid(), dto.getUpdatedAt());
            case REFUNDED -> cancelDelivery(dto.getOrderUuid());
            default -> log.debug("DeliveryService: статус {} заказа {} не требует действий доставки",
                    dto.getOrderStatus(), dto.getOrderUuid());
        }
    }

    /** Создать доставку (SEARCHING_COURIER, paidAt из события) и сразу попытаться назначить курьера. */
    @Transactional
    public void createDelivery(UUID orderUuid, LocalDateTime paidAt) {
        if (deliveryRepository.existsByOrderUuid(orderUuid)) {
            log.info("DeliveryService: доставка заказа {} уже существует — идемпотентный повтор", orderUuid);
            return;
        }
        Delivery delivery = Delivery.builder()
                .orderUuid(orderUuid)
                .status(DeliveryStatus.SEARCHING_COURIER)
                .paidAt(paidAt != null ? paidAt : LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        deliveryRepository.saveAndFlush(delivery);
        log.info("DeliveryService: создана доставка заказа {} (SEARCHING_COURIER, paidAt={})", orderUuid, paidAt);
        assignCourier(orderUuid);
    }

    /**
     * Назначить свободного курьера доставке: перебираем AVAILABLE по очереди регистрации,
     * атомарно занимаем первого удачного. Свободных нет — доставка остаётся в
     * SEARCHING_COURIER, DeliveryProgressJob повторит попытку позже.
     */
    @Transactional
    public void assignCourier(UUID orderUuid) {
        List<Courier> free = courierRepository.findByStatusOrderByIdAsc(CourierStatus.AVAILABLE);
        for (Courier courier : free) {
            if (courierRepository.tryAssign(courier.getId()) == 1) {
                if (deliveryRepository.assignCourier(orderUuid, courier.getId()) == 1) {
                    log.info("DeliveryService: курьер {} (id={}) назначен на доставку заказа {}",
                            courier.getUsername(), courier.getId(), orderUuid);
                    return;
                }
                // Доставку уже отменили/заняли (CAS=0) — занятого зря курьера освобождаем
                courierRepository.release(courier.getId());
                log.info("DeliveryService: доставка заказа {} больше не ищет курьера — курьер id={} освобождён",
                        orderUuid, courier.getId());
                return;
            }
        }
        log.debug("DeliveryService: свободных курьеров для заказа {} нет — повторит scheduler", orderUuid);
    }

    /**
     * Отмена доставки по REFUNDED (покупатель успел в 5-минутное окно): CAS-переход
     * SEARCHING_COURIER|ASSIGNED → CANCELLED + освобождение курьера, если был назначен.
     * IN_DELIVERY не отменяем — окно возврата уже закрыто order-service.
     */
    @Transactional
    public void cancelDelivery(UUID orderUuid) {
        Optional<Delivery> deliveryOpt = deliveryRepository.findByOrderUuid(orderUuid);
        if (deliveryOpt.isEmpty()) {
            log.debug("DeliveryService: доставки заказа {} нет — отмена не требуется", orderUuid);
            return;
        }
        Integer updated = deliveryRepository.transitionStatus(orderUuid,
                List.of(DeliveryStatus.SEARCHING_COURIER, DeliveryStatus.ASSIGNED), DeliveryStatus.CANCELLED);
        if (updated != null && updated > 0) {
            Long courierId = deliveryOpt.get().getCourierId();
            if (courierId != null) {
                courierRepository.release(courierId);
            }
            log.info("DeliveryService: доставка заказа {} отменена (возврат до старта доставки)", orderUuid);
        } else {
            log.info("DeliveryService: доставка заказа {} уже не отменяема (стадия {}) — пропускаем",
                    orderUuid, deliveryOpt.get().getStatus());
        }
    }

    /** Стадия IN_DELIVERY: заказ переведён в DELIVERING (200 от order-service), курьер «в пути». */
    @Transactional
    public void markInDelivery(UUID orderUuid) {
        deliveryRepository.transitionStatus(orderUuid,
                List.of(DeliveryStatus.SEARCHING_COURIER, DeliveryStatus.ASSIGNED), DeliveryStatus.IN_DELIVERY);
    }

    /** Завершение: DELIVERED + deliveredAt + освобождение курьера (вызывается после 200 от PATCH order-service). */
    @Transactional
    public void markDelivered(UUID orderUuid) {
        Optional<Delivery> deliveryOpt = deliveryRepository.findByOrderUuid(orderUuid);
        if (deliveryOpt.isEmpty()) {
            return;
        }
        Long courierId = deliveryOpt.get().getCourierId();
        Integer updated = deliveryRepository.transitionStatus(orderUuid,
                List.of(DeliveryStatus.IN_DELIVERY), DeliveryStatus.DELIVERED);
        if (updated != null && updated > 0) {
            deliveryRepository.findByOrderUuid(orderUuid).ifPresent(d -> {
                d.setDeliveredAt(LocalDateTime.now());
                deliveryRepository.save(d);
            });
            if (courierId != null) {
                courierRepository.release(courierId);
            }
            log.info("DeliveryService: заказ {} доставлен, курьер id={} освобождён", orderUuid, courierId);
        }
    }

    /** Повторное назначение для «зависших» SEARCHING_COURIER (вызывает DeliveryProgressJob). */
    @Transactional
    public void retryAssignment(Delivery delivery) {
        assignCourier(delivery.getOrderUuid());
    }
}
