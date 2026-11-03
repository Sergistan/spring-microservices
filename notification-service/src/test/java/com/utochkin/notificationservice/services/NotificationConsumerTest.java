package com.utochkin.notificationservice.services;

import com.utochkin.notificationservice.dto.AddressDto;
import com.utochkin.notificationservice.dto.OrderDtoForKafka;
import com.utochkin.notificationservice.dto.UserDto;
import com.utochkin.notificationservice.models.OrderRequest;
import com.utochkin.notificationservice.models.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class NotificationConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private RedisProcessedEventsStore processedEventsStore;

    @InjectMocks
    private NotificationConsumer consumer;

    private OrderDtoForKafka baseOrder() {
        return new OrderDtoForKafka(
                UUID.randomUUID(),
                123.45,
                Status.SUCCESS,
                LocalDateTime.of(2025,7,1,10,0),
                LocalDateTime.of(2025,7,1,10,5),
                new AddressDto("City", "Street", 1, 2),
                new UserDto("john", "John", "Doe", "john@doe.com"),
                List.of(new OrderRequest(UUID.randomUUID(), 2)),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("onOrderEvent отправляет письмо, когда ключ дедупликации захвачен")
    void onOrderEvent_sendsEmail() {
        var order = baseOrder();
        given(processedEventsStore.tryAcquire(order.getEventId())).willReturn(true);

        consumer.onOrderEvent(order);

        then(emailService).should()
                .sendOrderNotification(
                        eq("john@doe.com"),
                        eq("Номер заказа " + order.getOrderUuid()),
                        any()
                );
    }

    @Test
    @DisplayName("onOrderEvent не должен отправлять письмо при пустом email у пользователя")
    void onOrderEvent_skipsWhenNoEmail() {
        var base = baseOrder();
        var order = new OrderDtoForKafka(
                base.getOrderUuid(),
                base.getTotalAmount(),
                Status.FAILED,
                base.getCreatedAt(),
                base.getUpdatedAt(),
                base.getAddressDto(),
                new UserDto("john", "John", "Doe", ""),
                base.getOrderRequests(),
                base.getPaymentId(),
                base.getEventId()
        );
        given(processedEventsStore.tryAcquire(order.getEventId())).willReturn(true);

        consumer.onOrderEvent(order);

        then(emailService).should(never()).sendOrderNotification(any(),any(),any());
    }

    @Test
    @DisplayName("Дубликат (tryAcquire=false) — письмо не отправляется, offset ack")
    void onOrderEvent_duplicateEvent_skipped() {
        var order = baseOrder();
        given(processedEventsStore.tryAcquire(order.getEventId())).willReturn(false);

        consumer.onOrderEvent(order);

        then(emailService).should(never()).sendOrderNotification(any(), any(), any());
        then(processedEventsStore).should(never()).release(any());
    }

    @Test
    @DisplayName("Сбой отправки письма — ключ дедупликации освобождается (компенсация), исключение наружу")
    void onOrderEvent_emailFails_releasesKeyAndRethrows() {
        var order = baseOrder();
        given(processedEventsStore.tryAcquire(order.getEventId())).willReturn(true);
        doThrow(new RuntimeException("smtp down")).when(emailService)
                .sendOrderNotification(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> consumer.onOrderEvent(order))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("smtp down");

        then(processedEventsStore).should().release(order.getEventId());
    }

    @Test
    @DisplayName("Событие без eventId обрабатывается без дедупликации")
    void onOrderEvent_nullEventId_processedWithoutDedup() {
        var order = baseOrder();
        order.setEventId(null);

        consumer.onOrderEvent(order);

        verifyNoInteractions(processedEventsStore);
        then(emailService).should().sendOrderNotification(eq("john@doe.com"), any(), any());
    }

}
