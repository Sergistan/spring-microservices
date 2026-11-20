package com.utochkin.deliveryservice.services;

import com.utochkin.deliveryservice.models.Courier;
import com.utochkin.deliveryservice.models.Delivery;
import com.utochkin.deliveryservice.models.DeliveryStatus;
import com.utochkin.deliveryservice.repositories.CourierRepository;
import com.utochkin.deliveryservice.repositories.DeliveryRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Двигатель стадий доставки (каждые delivery.progress.fixed-delay, по умолчанию 30 с).
 * Тайминги конфигурируются (для E2E-тестов сжимаются до секунд):
 *  - delivery.timing.start-after-minutes (5): T+5 минут с оплаты → Feign POST /deliveries
 *    (заказ SUCCESS→DELIVERING), доставка → IN_DELIVERY. 409 = возврат успел раньше
 *    → доставка CANCELLED + курьер освобождён;
 *  - delivery.timing.deliver-after-minutes (2): «курьер в пути» → Feign PATCH /deliveries
 *    (заказ DELIVERING→DELIVERED), доставка DELIVERED + курьер освобождён;
 *  - delivery.timing.reassign-after-minutes (1): повторное назначение для SEARCHING_COURIER.
 *
 * Ошибка одной доставки не валит цикл (try/catch на каждую — по образцу ReconciliationJob
 * order-service). Feign-вызовы обёрнуты в OrderDeliveryClient (retry/circuit breaker;
 * 409-бизнес-ответ не ретраится).
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class DeliveryProgressJob {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryService deliveryService;
    private final OrderDeliveryClient orderDeliveryClient;
    private final CourierRepository courierRepository;

    @Value("${delivery.timing.start-after-minutes:5}")
    private long startAfterMinutes;

    @Value("${delivery.timing.deliver-after-minutes:2}")
    private long deliverAfterMinutes;

    @Value("${delivery.timing.reassign-after-minutes:1}")
    private long reassignAfterMinutes;

    /** ФИО назначенного курьера доставки ("Имя Фамилия"); null, если курьер ещё не назначен/не найден. */
    private String courierFullName(Delivery delivery) {
        if (delivery.getCourierId() == null) {
            return null;
        }
        return courierRepository.findById(delivery.getCourierId())
                .map(c -> ((c.getFirstName() + " " + c.getLastName()).trim()))
                .orElse(null);
    }

    @Scheduled(fixedDelayString = "${delivery.progress.fixed-delay:30000}")
    public void progress() {
        LocalDateTime now = LocalDateTime.now();

        // 1) Повторное назначение курьеров для «зависших» SEARCHING_COURIER
        List<Delivery> searching = deliveryRepository.findByStatusAndUpdatedAtBefore(
                DeliveryStatus.SEARCHING_COURIER, now.minusMinutes(reassignAfterMinutes));
        for (Delivery delivery : searching) {
            try {
                deliveryService.retryAssignment(delivery);
            } catch (Exception e) {
                log.warn("DeliveryProgressJob: повторное назначение для заказа {} не удалось: {}",
                        delivery.getOrderUuid(), e.getMessage());
            }
        }

        // 2) Старт доставки: T+5 минут с момента оплаты истёк
        List<Delivery> toStart = deliveryRepository.findByStatusInAndPaidAtBefore(
                List.of(DeliveryStatus.SEARCHING_COURIER, DeliveryStatus.ASSIGNED), now.minusMinutes(startAfterMinutes));
        for (Delivery delivery : toStart) {
            try {
                String courierName = courierFullName(delivery);
                orderDeliveryClient.startDelivery(delivery.getOrderUuid(), courierName);
                deliveryService.markInDelivery(delivery.getOrderUuid());
                log.info("DeliveryProgressJob: заказ {} передан в доставку (DELIVERING, курьер: {}), стадия IN_DELIVERY",
                        delivery.getOrderUuid(), courierName != null ? courierName : "не назначен");
            } catch (FeignException fe) {
                if (fe.status() == HttpStatus.CONFLICT.value()) {
                    // Заказ уже не SUCCESS — покупатель успел вернуть в 5-минутном окне
                    deliveryService.cancelDelivery(delivery.getOrderUuid());
                    log.info("DeliveryProgressJob: заказ {} уже возвращён (409) — доставка отменена, курьер освобождён",
                            delivery.getOrderUuid());
                } else {
                    log.warn("DeliveryProgressJob: старт доставки {} не удался (HTTP {}): {}",
                            delivery.getOrderUuid(), fe.status(), fe.getMessage());
                }
            } catch (Exception e) {
                log.warn("DeliveryProgressJob: старт доставки {} не удался: {}",
                        delivery.getOrderUuid(), e.getMessage());
            }
        }

        // 3) Завершение: «курьер в пути» дольше deliver-after-minutes
        List<Delivery> toComplete = deliveryRepository.findByStatusAndUpdatedAtBefore(
                DeliveryStatus.IN_DELIVERY, now.minusMinutes(deliverAfterMinutes));
        for (Delivery delivery : toComplete) {
            try {
                orderDeliveryClient.completeDelivery(delivery.getOrderUuid());
                deliveryService.markDelivered(delivery.getOrderUuid());
                log.info("DeliveryProgressJob: заказ {} доставлен покупателю (DELIVERED)", delivery.getOrderUuid());
            } catch (FeignException fe) {
                if (fe.status() == HttpStatus.CONFLICT.value()) {
                    // Заказ не в DELIVERING — аномалия (вручную изменён?): доставку всё равно завершаем,
                    // чтобы не зацикливаться и освободить курьера
                    deliveryService.markDelivered(delivery.getOrderUuid());
                    log.warn("DeliveryProgressJob: заказ {} ответил 409 на завершение — доставка закрыта принудительно",
                            delivery.getOrderUuid());
                } else {
                    log.warn("DeliveryProgressJob: завершение доставки {} не удалось (HTTP {}): {}",
                            delivery.getOrderUuid(), fe.status(), fe.getMessage());
                }
            } catch (Exception e) {
                log.warn("DeliveryProgressJob: завершение доставки {} не удалось: {}",
                        delivery.getOrderUuid(), e.getMessage());
            }
        }
    }
}
