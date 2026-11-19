package com.utochkin.orderservice.services;

import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.repositories.OrderRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Фоновая джоба самовосстановления: находит заказы, застрявшие в PROCESSING/STOCK_PENDING дольше
 * order.reconciliation.stuck-threshold-minutes, и для каждого вызывает OrderService.reconcile —
 * сверку с payment-service (платёж прошёл/возвращён/отсутствует) с последующей фиксацией
 * финального статуса или откатом списания остатков.
 */
@Component
@Log4j2
public class ReconciliationJob {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.reconciliation.stuck-threshold-minutes:10}")
    private long stuckThresholdMinutes;

    public ReconciliationJob(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    /**
     * Такт джобы (fixed-delay из config-repo): взять до 50 заказов в PROCESSING/STOCK_PENDING,
     * не обновлявшихся дольше stuck-threshold-minutes, и сверить каждый с payment через
     * OrderService.reconcile. Ошибка одного заказа не валит цикл — он повторится в следующем такте.
     */
    @Scheduled(fixedDelayString = "${order.reconciliation.fixed-delay:60000}")
    public void reconcile() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(stuckThresholdMinutes);
        List<Order> stuck = orderRepository.findTop50ByOrderStatusInAndUpdatedAtBefore(List.of(Status.PROCESSING, Status.STOCK_PENDING), threshold);
        if (stuck.isEmpty()) {
            return;
        }
        log.info("ReconciliationJob: найдено {} зависших заказов в [PROCESSING, STOCK_PENDING] (порог {} мин)",
                stuck.size(), stuckThresholdMinutes);

        for (Order order : stuck) {
            try {
                orderService.reconcile(order);
            } catch (Exception e) {
                log.warn("ReconciliationJob: не удалось сверить заказ {} — будет повторено на следующем цикле: {}",
                        order.getOrderUuid(), e.getMessage());
            }
        }
    }
}
