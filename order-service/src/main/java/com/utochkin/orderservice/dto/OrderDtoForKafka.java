package com.utochkin.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.utochkin.orderservice.models.Address;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.request.OrderRequest;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class OrderDtoForKafka implements Serializable {

    private final UUID orderUuid;

    private final Double totalAmount;

    private final Status orderStatus;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private final LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;

    private final Address address;

    private final UserDto userDto;

    private final List<OrderRequest> orderRequests;

    private UUID paymentId;
}
