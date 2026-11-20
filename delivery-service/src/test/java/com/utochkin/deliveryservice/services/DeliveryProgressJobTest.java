package com.utochkin.deliveryservice.services;

import com.utochkin.deliveryservice.models.Delivery;
import com.utochkin.deliveryservice.models.DeliveryStatus;
import com.utochkin.deliveryservice.repositories.DeliveryRepository;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DeliveryProgressJobTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private OrderDeliveryClient orderDeliveryClient;

    @InjectMocks
    private DeliveryProgressJob job;

    private final UUID orderUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(job, "startAfterMinutes", 5L);
        ReflectionTestUtils.setField(job, "deliverAfterMinutes", 2L);
        ReflectionTestUtils.setField(job, "reassignAfterMinutes", 1L);
        given(deliveryRepository.findByStatusAndUpdatedAtBefore(any(), any())).willReturn(List.of());
        given(deliveryRepository.findByStatusInAndPaidAtBefore(any(), any())).willReturn(List.of());
    }

    private Delivery delivery(DeliveryStatus status) {
        return Delivery.builder().orderUuid(orderUuid).status(status)
                .paidAt(LocalDateTime.now().minusMinutes(10)).build();
    }

    private FeignException feignStatus(int code) {
        Request request = Request.create(Request.HttpMethod.POST, "http://order/x",
                Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(code).reason("r").request(request).build();
        return FeignException.errorStatus("OrderServiceClient#startDelivery", response);
    }

    @Test
    @DisplayName("T+5мин: 200 от order-service → markInDelivery")
    void start_success() {
        given(deliveryRepository.findByStatusInAndPaidAtBefore(any(), any()))
                .willReturn(List.of(delivery(DeliveryStatus.ASSIGNED)));

        job.progress();

        then(orderDeliveryClient).should().startDelivery(orderUuid);
        then(deliveryService).should().markInDelivery(orderUuid);
        then(deliveryService).should(never()).cancelDelivery(any());
    }

    @Test
    @DisplayName("T+5мин: 409 (возврат успел) → cancelDelivery + освобождение курьера, без markInDelivery")
    void start_conflict_cancels() {
        given(deliveryRepository.findByStatusInAndPaidAtBefore(any(), any()))
                .willReturn(List.of(delivery(DeliveryStatus.ASSIGNED)));
        willThrow(feignStatus(409)).given(orderDeliveryClient).startDelivery(orderUuid);

        job.progress();

        then(deliveryService).should().cancelDelivery(orderUuid);
        then(deliveryService).should(never()).markInDelivery(any());
    }

    @Test
    @DisplayName("T+5мин: технический сбой (503) → ничего не меняем, джоба повторит на следующем такте")
    void start_technicalFailure_noChanges() {
        given(deliveryRepository.findByStatusInAndPaidAtBefore(any(), any()))
                .willReturn(List.of(delivery(DeliveryStatus.ASSIGNED)));
        willThrow(feignStatus(503)).given(orderDeliveryClient).startDelivery(orderUuid);

        job.progress();

        then(deliveryService).should(never()).markInDelivery(any());
        then(deliveryService).should(never()).cancelDelivery(any());
    }

    @Test
    @DisplayName("Завершение: 200 от PATCH → markDelivered")
    void complete_success() {
        given(deliveryRepository.findByStatusAndUpdatedAtBefore(any(), any()))
                .willReturn(List.of())                    // 1-й вызов: SEARCHING_COURIER повторное назначение
                .willReturn(List.of(delivery(DeliveryStatus.IN_DELIVERY))); // 2-й вызов: IN_DELIVERY

        job.progress();

        then(orderDeliveryClient).should().completeDelivery(orderUuid);
        then(deliveryService).should().markDelivered(orderUuid);
    }

    @Test
    @DisplayName("Завершение: 409 (заказ не в DELIVERING — аномалия) → доставку закрываем принудительно")
    void complete_conflict_forcesClose() {
        given(deliveryRepository.findByStatusAndUpdatedAtBefore(any(), any()))
                .willReturn(List.of())
                .willReturn(List.of(delivery(DeliveryStatus.IN_DELIVERY)));
        willThrow(feignStatus(409)).given(orderDeliveryClient).completeDelivery(orderUuid);

        job.progress();

        then(deliveryService).should().markDelivered(orderUuid);
    }

    @Test
    @DisplayName("Повторное назначение: зависший SEARCHING_COURIER → retryAssignment")
    void reassign_stuckSearching() {
        given(deliveryRepository.findByStatusAndUpdatedAtBefore(any(), any()))
                .willReturn(List.of(delivery(DeliveryStatus.SEARCHING_COURIER)))
                .willReturn(List.of());

        job.progress();

        then(deliveryService).should().retryAssignment(any(Delivery.class));
    }
}
