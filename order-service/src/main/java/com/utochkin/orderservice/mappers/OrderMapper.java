package com.utochkin.orderservice.mappers;

import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.dto.UserDto;
import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.request.OrderRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(target = "userDto", expression = "java(userDto)")
    @Mapping(target = "orderRequests", expression = "java(orderRequests)")
    OrderDto toDto(Order order, UserDto userDto, List<OrderRequest> orderRequests);

    Order toEntity(OrderDto orderDto);
}
