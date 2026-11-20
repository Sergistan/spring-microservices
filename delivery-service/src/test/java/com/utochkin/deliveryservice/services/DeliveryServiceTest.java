package com.utochkin.deliveryservice.services;

import com.utochkin.deliveryservice.dto.OrderDtoForKafka;
import com.utochkin.deliveryservice.dto.Status;
import com.utochkin.deliveryservice.models.Courier;
import com.utochkin.deliveryservice.models.CourierStatus;
import com.utochkin.deliveryservice.repositories.CourierRepository;
import com.utochkin.deliveryservice.repositories.DeliveryRepository;
import com.utochkin.deliveryservice.repositories.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private CourierRepository courierRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private DeliveryService deliveryService;

    private final UUID orderUuid = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    private OrderDtoForKafka event(Status status) {
        OrderDtoForKafka dto = new OrderDtoForKafka();
        dto.setOrderUuid(orderUuid);
        dto.setOrderStatus(status);
        dto.setEventId(eventId);
        dto.setUpdatedAt(LocalDateTime.of(2026, 11, 20, 12, 0));
        return dto;
    }

    @Test
    @DisplayName("processEvent: дубль event_id (DataIntegrityViolation) — никаких действий")
    void processEvent_duplicateMarker_skips() {
        given(processedEventRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        deliveryService.processEvent(event(Status.SUCCESS));

        then(deliveryRepository).should(never()).existsByOrderUuid(any());
        then(deliveryRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("processEvent: SUCCESS → создаётся Delivery(SEARCHING_COURIER) и вызывается назначение курьера")
    void processEvent_success_createsDelivery() {
        given(processedEventRepository.saveAndFlush(any())).willReturn(null);
        given(deliveryRepository.existsByOrderUuid(orderUuid)).willReturn(false);
        given(deliveryRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
        given(courierRepository.findByStatusOrderByIdAsc(CourierStatus.AVAILABLE)).willReturn(List.of());

        deliveryService.processEvent(event(Status.SUCCESS));

        then(deliveryRepository).should().saveAndFlush(any());
        then(courierRepository).should().findByStatusOrderByIdAsc(CourierStatus.AVAILABLE);
    }

    @Test
    @DisplayName("processEvent: повторный SUCCESS с другой датой → доставка не пересоздаётся")
    void processEvent_successIdempotent() {
        given(processedEventRepository.saveAndFlush(any())).willReturn(null);
        given(deliveryRepository.existsByOrderUuid(orderUuid)).willReturn(true);

        deliveryService.processEvent(event(Status.SUCCESS));

        then(deliveryRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("processEvent: REFUNDED → cancelDelivery (CAS + освобождение курьера)")
    void processEvent_refunded_cancels() {
        given(processedEventRepository.saveAndFlush(any())).willReturn(null);
        com.utochkin.deliveryservice.models.Delivery delivery =
                com.utochkin.deliveryservice.models.Delivery.builder().orderUuid(orderUuid).courierId(7L).build();
        given(deliveryRepository.findByOrderUuid(orderUuid)).willReturn(Optional.of(delivery));
        given(deliveryRepository.transitionStatus(any(), any(), any())).willReturn(1);

        deliveryService.processEvent(event(Status.REFUNDED));

        then(courierRepository).should().release(7L);
    }

    @Test
    @DisplayName("assignCourier: первый свободный курьер занимается атомарно и вешается на доставку")
    void assignCourier_success() {
        Courier free = Courier.builder().id(11L).username("courier-1").status(CourierStatus.AVAILABLE).build();
        given(courierRepository.findByStatusOrderByIdAsc(CourierStatus.AVAILABLE)).willReturn(List.of(free));
        given(courierRepository.tryAssign(11L)).willReturn(1);
        given(deliveryRepository.assignCourier(orderUuid, 11L)).willReturn(1);

        deliveryService.assignCourier(orderUuid);

        then(courierRepository).should(never()).release(any());
    }

    @Test
    @DisplayName("assignCourier: курьера успел занять конкурент (tryAssign=0 у всех) — доставки не назначены")
    void assignCourier_allTaken() {
        Courier c1 = Courier.builder().id(1L).status(CourierStatus.AVAILABLE).build();
        Courier c2 = Courier.builder().id(2L).status(CourierStatus.AVAILABLE).build();
        given(courierRepository.findByStatusOrderByIdAsc(CourierStatus.AVAILABLE)).willReturn(List.of(c1, c2));
        AtomicInteger calls = new AtomicInteger();
        given(courierRepository.tryAssign(any())).willAnswer(inv -> {
            calls.incrementAndGet();
            return 0;
        });

        deliveryService.assignCourier(orderUuid);

        assertThat(calls.get()).isEqualTo(2);
        then(deliveryRepository).should(never()).assignCourier(any(), any());
    }

    @Test
    @DisplayName("assignCourier: доставка уже отменена (assignCourier=0) — зазря занятый курьер освобождается")
    void assignCourier_deliveryGone_releasesCourier() {
        Courier free = Courier.builder().id(3L).status(CourierStatus.AVAILABLE).build();
        given(courierRepository.findByStatusOrderByIdAsc(CourierStatus.AVAILABLE)).willReturn(List.of(free));
        given(courierRepository.tryAssign(3L)).willReturn(1);
        given(deliveryRepository.assignCourier(orderUuid, 3L)).willReturn(0);

        deliveryService.assignCourier(orderUuid);

        then(courierRepository).should().release(3L);
    }

    @Test
    @DisplayName("markDelivered: CAS=1 → deliveredAt фиксируется и курьер освобождается")
    void markDelivered_releasesCourier() {
        com.utochkin.deliveryservice.models.Delivery delivery =
                com.utochkin.deliveryservice.models.Delivery.builder().orderUuid(orderUuid).courierId(9L).build();
        given(deliveryRepository.findByOrderUuid(orderUuid)).willReturn(Optional.of(delivery));
        given(deliveryRepository.transitionStatus(any(), any(), any())).willReturn(1);
        given(deliveryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        deliveryService.markDelivered(orderUuid);

        then(courierRepository).should().release(9L);
        then(deliveryRepository).should().save(any(com.utochkin.deliveryservice.models.Delivery.class));
    }
}
