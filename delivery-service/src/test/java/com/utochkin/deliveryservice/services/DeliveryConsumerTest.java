package com.utochkin.deliveryservice.services;

import com.utochkin.deliveryservice.dto.OrderDtoForKafka;
import com.utochkin.deliveryservice.dto.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class DeliveryConsumerTest {

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private DeliveryConsumer consumer;

    @Test
    @DisplayName("Успешная обработка → processEvent + ack")
    void delegatesAndAcks() {
        OrderDtoForKafka dto = new OrderDtoForKafka();
        dto.setOrderUuid(UUID.randomUUID());
        dto.setOrderStatus(Status.SUCCESS);
        dto.setEventId(UUID.randomUUID());
        dto.setUpdatedAt(LocalDateTime.now());

        consumer.onOrderEvent(dto, ack);

        then(deliveryService).should().processEvent(dto);
        then(ack).should().acknowledge();
    }

    @Test
    @DisplayName("Исключение обработки → без ack (ретраи/DLT), deliveryService вызван")
    void exceptionMeansNoAck() {
        OrderDtoForKafka dto = new OrderDtoForKafka();
        dto.setOrderUuid(UUID.randomUUID());
        dto.setOrderStatus(Status.SUCCESS);
        doThrow(new RuntimeException("db down")).when(deliveryService).processEvent(dto);

        try {
            consumer.onOrderEvent(dto, ack);
        } catch (RuntimeException expected) {
            // исключение пробрасывается наружу для DefaultErrorHandler (ретраи → DLT)
        }

        then(deliveryService).should().processEvent(dto);
        then(ack).should(never()).acknowledge();
    }
}
