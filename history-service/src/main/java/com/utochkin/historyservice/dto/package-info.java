/**
 * Контракт события topic-orders на стороне консьюмера history-service.
 * {@link com.utochkin.historyservice.dto.OrderDtoForKafka} и его части (AddressDto, UserDto, OrderRequest, Status)
 * десериализуются из Kafka и сохраняются в историю пользователя (MongoDB);
 * полное имя класса связано spring.json.type.mapping с продюсером (order-service), поэтому пакет менять нельзя.
 */
package com.utochkin.historyservice.dto;
