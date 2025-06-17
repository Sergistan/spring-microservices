package com.utochkin.orderservice.mappers;

import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.dto.UserDto;
import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.request.OrderRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(target = "userDto", expression = "java(userDto)")
    @Mapping(target = "addressDto", expression = "java(addressDto)")
    @Mapping(target = "orderRequests", expression = "java(orderRequests)")
    OrderDto toDto(Order order, UserDto userDto, AddressDto addressDto, List<OrderRequest> orderRequests);

    @Mapping(target = "userDto", expression = "java(userDto)")
    @Mapping(target = "addressDto", expression = "java(addressDto)")
    @Mapping(target = "orderRequests", expression = "java(orderRequests)")
    OrderDtoForKafka toDtoForKafka(Order order, UserDto userDto, AddressDto addressDto, List<OrderRequest> orderRequests);
}
