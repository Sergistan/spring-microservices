package com.utochkin.deliveryservice.services;

import com.utochkin.deliveryservice.dto.OrderDtoForKafka;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Третий консьюмер topic-orders (группа delivery-group): события заказа.
 * Вся логика и идемпотентность — в DeliveryService.processEvent (одна транзакция
 * с маркером processed_events). Исключение наружу = без ack → ретраи → topic-orders.DLT
 * (KafkaErrorConfig); дубль после ретрая безопасен благодаря маркеру.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class DeliveryConsumer {

    public static final String TOPIC_ORDERS = "topic-orders";
    public static final String GROUP_ID = "delivery-group";

    private final DeliveryService deliveryService;

    @KafkaListener(topics = TOPIC_ORDERS, groupId = GROUP_ID)
    public void onOrderEvent(OrderDtoForKafka orderDto, Acknowledgment ack) {
        log.info("DeliveryConsumer: событие {} заказа {} (статус {})",
                orderDto.getEventId(), orderDto.getOrderUuid(), orderDto.getOrderStatus());
        // ack только после успешной обработки: исключение = без ack → ретраи → topic-orders.DLT
        // (дубль после ретрая безопасен — processEvent идемпотентен по маркеру processed_events)
        deliveryService.processEvent(orderDto);
        ack.acknowledge();
    }
}
