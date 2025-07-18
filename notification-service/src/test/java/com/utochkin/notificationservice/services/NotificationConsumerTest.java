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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class NotificationConsumerTest {

    @Mock
    private EmailService emailService;

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
                UUID.randomUUID()
        );
    }

    @Test
    @DisplayName("onOrderEvent должен отправлять электронное письмо при наличии email у пользователя")
    void onOrderEvent_sendsEmail() {
        var order = baseOrder();

        consumer.onOrderEvent(order);

        then(emailService).should()
                .sendOrderNotification(
                        eq("john@doe.com"),
                        eq("Номер заказа " + order.getOrderUuid()),
                        contains("Ваш заказ № " + order.getOrderUuid())
                );
    }

    @Test
    @DisplayName("onOrderEvent не должен отправлять электронное письмо при пустом email у пользователя")
    void onOrderEvent_skipsWhenNoEmail() {
        var order = new OrderDtoForKafka(
                baseOrder().getOrderUuid(),
                baseOrder().getTotalAmount(),
                Status.FAILED,
                baseOrder().getCreatedAt(),
                baseOrder().getUpdatedAt(),
                baseOrder().getAddressDto(),
                new UserDto("john","John","Doe",""),
                baseOrder().getOrderRequests(),
                baseOrder().getPaymentId()
        );

        consumer.onOrderEvent(order);

        then(emailService).should(never()).sendOrderNotification(any(),any(),any());
    }

}
