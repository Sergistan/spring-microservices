/**
 * Контракт события topic-orders на стороне консьюмера notification-service.
 * {@link com.utochkin.notificationservice.dto.OrderDtoForKafka} и его части (AddressDto, UserDto, OrderRequest, Status)
 * десериализуются из Kafka; полное имя класса связано spring.json.type.mapping с продюсером (order-service),
 * поэтому пакет менять нельзя.
 */
package com.utochkin.notificationservice.dto;
