/**
 * Исходящие данные order-service: ответ создания заказа {@link com.utochkin.orderservice.dto.OrderDto},
 * тело ошибки {@link com.utochkin.orderservice.dto.ErrorResponse} и событие
 * {@link com.utochkin.orderservice.dto.OrderDtoForKafka} — публикуется в Kafka (topic-orders)
 * через transactional outbox; полное имя класса участвует в spring.json.type.mapping консьюмеров
 * (notification/history), поэтому его пакет менять нельзя.
 */
package com.utochkin.orderservice.dto;
