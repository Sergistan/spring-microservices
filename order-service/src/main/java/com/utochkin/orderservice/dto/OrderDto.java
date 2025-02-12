package com.utochkin.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.utochkin.orderservice.models.Address;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.request.OrderRequest;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDto(Double totalAmount,
                       Status orderStatus,
                       @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm") LocalDateTime createdAt,
                       Address address,
                       UserDto userDto,
                       List<OrderRequest> orderRequests) {
}
