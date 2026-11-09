package com.utochkin.orderservice.services;

import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.repositories.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReconciliationJobTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private ReconciliationJob reconciliationJob;

    private Order stuckOrder(UUID orderUuid, Status status) {
        Order order = new Order();
        order.setOrderUuid(orderUuid);
        order.setOrderStatus(status);
        order.setUpdatedAt(LocalDateTime.now().minusMinutes(30));
        return order;
    }

    @Test
    @DisplayName("reconcile: находит зависшие заказы обоих статусов и делегирует OrderService.reconcile")
    void reconcile_delegatesStuckOrders() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Order orderOne = stuckOrder(first, Status.PROCESSING);
        Order orderTwo = stuckOrder(second, Status.STOCK_PENDING);
        when(orderRepository.findTop50ByOrderStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(List.of(orderOne, orderTwo));

        reconciliationJob.reconcile();

        verify(orderService).reconcile(orderOne);
        verify(orderService).reconcile(orderTwo);
    }

    @Test
    @DisplayName("reconcile: сбой одного заказа — остальные всё равно обрабатываются")
    void reconcile_oneFails_continuesWithOthers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Order orderOne = stuckOrder(first, Status.PROCESSING);
        Order orderTwo = stuckOrder(second, Status.STOCK_PENDING);
        when(orderRepository.findTop50ByOrderStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(List.of(orderOne, orderTwo));
        org.mockito.Mockito.doThrow(new RuntimeException("payment down"))
                .when(orderService).reconcile(orderOne);

        reconciliationJob.reconcile();

        verify(orderService).reconcile(orderTwo);
    }

    @Test
    @DisplayName("reconcile: нет зависших заказов — без обращений к OrderService")
    void reconcile_noStuckOrders_noCalls() {
        when(orderRepository.findTop50ByOrderStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(List.of());

        reconciliationJob.reconcile();

        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("reconcile: выборка идёт по списку статусов [PROCESSING, STOCK_PENDING]")
    void reconcile_queriesBothStatuses() {
        when(orderRepository.findTop50ByOrderStatusInAndUpdatedAtBefore(anyCollection(), any()))
                .thenReturn(List.of());

        reconciliationJob.reconcile();

        verify(orderRepository).findTop50ByOrderStatusInAndUpdatedAtBefore(
                eq(List.of(Status.PROCESSING, Status.STOCK_PENDING)), any());
    }
}
