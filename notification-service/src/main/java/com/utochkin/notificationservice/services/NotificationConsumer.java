package com.utochkin.notificationservice.services;

import com.utochkin.notificationservice.dto.OrderDtoForKafka;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Консьюмер события topic-orders -> email-уведомление пользователю.
 * Порядок веток: без eventId -> отправляем сразу; дубль (Redis) -> пропускаем;
 * иначе отправка и только при успехе ack (MANUAL_IMMEDIATE). Сбой отправки:
 * ключ дедупликации освобождается, ack НЕ делается -> ретраи -> topic-orders.DLT.
 */
@Service
@AllArgsConstructor
@Log4j2
public class NotificationConsumer {

    private final EmailService emailService;
    private final RedisProcessedEventsStore processedEventsStore;

    /** Приём события: дедупликация по eventId -> отправка письма -> ack (см. javadoc класса). */
    @KafkaListener(topics = "topic-orders", groupId = "notification-group")
    public void onOrderEvent(OrderDtoForKafka orderDto, Acknowledgment ack) {
        log.info("NotificationConsumer: получено сообщение из Kafka: {}", orderDto);

        if (orderDto.getEventId() == null) {
            log.warn("NotificationConsumer: событие без eventId для заказа {} — обрабатываем без дедупликации", orderDto.getOrderUuid());
            sendNotification(orderDto);
            ack.acknowledge();
            return;
        }

        if (!processedEventsStore.tryAcquire(orderDto.getEventId())) {
            log.info("NotificationConsumer: событие {} для заказа {} уже обработано — пропускаем (дедупликация)",
                    orderDto.getEventId(), orderDto.getOrderUuid());
            ack.acknowledge();
            return;
        }

        try {
            sendNotification(orderDto);
            ack.acknowledge();
        } catch (Exception e) {
            processedEventsStore.release(orderDto.getEventId());
            log.warn("NotificationConsumer: не удалось отправить уведомление для события {} — ключ дедупликации освобождён для повторной обработки",
                    orderDto.getEventId());
            throw e;
        }
    }

    /**
     * Отправка письма на email из события (нет email -> warn и пропуск, это не ошибка).
     * DELIVERED -> письмо не шлём: уведомили уже при DELIVERING («будет доставлен в течение дня»).
     */
    private void sendNotification(OrderDtoForKafka orderDto) {
        if (orderDto.getOrderStatus() == com.utochkin.notificationservice.dto.Status.DELIVERED) {
            log.info("NotificationConsumer: заказ {} доставлен — письмо не отправляем (уведомление ушло при DELIVERING)",
                    orderDto.getOrderUuid());
            return;
        }
        String recipientEmail = orderDto.getUserDto().email();
        if (recipientEmail != null && !recipientEmail.isEmpty()) {
            String subject = "Номер заказа " + orderDto.getOrderUuid();
            String text = createEmailText(orderDto);
            emailService.sendOrderNotification(recipientEmail, subject, text);
        } else {
            log.warn("NotificationConsumer: у пользователя {} нет email, пропускаем уведомление", orderDto.getUserDto().username());
        }
    }

        /** Текст письма по статусу: SUCCESS — детали/адрес/позиции, FAILED/REFUNDED/DELIVERING — шаблоны (DELIVERED сюда не попадает — письмо не шлём). */
    private String createEmailText(OrderDtoForKafka orderDto) {
            log.debug("NotificationConsumer: формируем текст письма для заказа {}", orderDto.getOrderUuid());

            StringBuilder sb = new StringBuilder();

            String productList = orderDto.getOrderRequests().stream()
                    .map(req -> "Номер товара: " + req.articleId() + ", Количество: " + req.quantity() + " шт.")
                    .collect(Collectors.joining("; "));

            switch (orderDto.getOrderStatus()){
                case SUCCESS -> {
                    sb.append("Здравствуйте, ")
                            .append(orderDto.getUserDto().firstName())
                            .append("!\n\n");
                    sb.append("Ваш заказ № ")
                            .append(orderDto.getOrderUuid())
                            .append(" на сумму ")
                            .append(orderDto.getTotalAmount())
                            .append(" рублей успешно оплачен.\n\n")
                            .append("Номер платежа: ")
                            .append(orderDto.getPaymentId())
                            .append(".\n\n");
                    sb.append("Заказ будет отправлен по адресу: ")
                            .append(orderDto.getAddressDto().city())
                            .append(", ")
                            .append(orderDto.getAddressDto().street())
                            .append(", дом № ")
                            .append(orderDto.getAddressDto().houseNumber())
                            .append(", квартира № ")
                            .append(orderDto.getAddressDto().apartmentNumber())
                            .append(".\n\n");
                    sb.append("Дата создания заказа: ")
                            .append(orderDto.getCreatedAt().toString().replace("T", " "))
                            .append(".\n\n");
                    sb.append("Список товаров в заказе: ")
                            .append(productList)
                            .append("\n");
                    }

                case FAILED -> {
                    sb.append("Здравствуйте, ")
                            .append(orderDto.getUserDto().firstName())
                            .append("!\n\n");
                    sb.append("Ваш заказ №")
                            .append(orderDto.getOrderUuid())
                            .append(" на сумму ")
                            .append(orderDto.getTotalAmount())
                            .append(" не получилось оплатить.")
                            .append(" Пожалуйста проверьте свой баланс.")
                            .append("\n");
                }
                case REFUNDED -> {
                    sb.append("Здравствуйте, ")
                            .append(orderDto.getUserDto().firstName())
                            .append("!\n\n");
                    sb.append("Ваш заказ №")
                            .append(orderDto.getOrderUuid())
                            .append(" успешно отменен.")
                            .append(" Ваши средства в размере ")
                            .append(orderDto.getTotalAmount())
                            .append(" в ближайшее время вернутся на счет.")
                            .append("\n");
                }
                case DELIVERING -> {
                    sb.append("Здравствуйте, ")
                            .append(orderDto.getUserDto().firstName())
                            .append("!\n\n");
                    sb.append("Ваш заказ №")
                            .append(orderDto.getOrderUuid())
                            .append(" принят курьером и доставляется по адресу: ")
                            .append(orderDto.getAddressDto().city())
                            .append(", ")
                            .append(orderDto.getAddressDto().street())
                            .append(", дом № ")
                            .append(orderDto.getAddressDto().houseNumber())
                            .append(", квартира № ")
                            .append(orderDto.getAddressDto().apartmentNumber())
                            .append(".\n\n");
                    sb.append("Заказ будет доставлен в течение дня.")
                            .append("\n");
                }
            }

            sb.append("\nСпасибо, что выбираете наш сервис!");
            log.info("Сформировано сообщения для отправке по почте: {}", sb.toString());
            return sb.toString();
        }

}
