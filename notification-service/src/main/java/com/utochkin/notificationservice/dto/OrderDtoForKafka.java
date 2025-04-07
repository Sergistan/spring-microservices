package com.utochkin.notificationservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.utochkin.notificationservice.models.Address;
import com.utochkin.notificationservice.models.OrderRequest;
import com.utochkin.notificationservice.models.Status;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDtoForKafka implements Serializable {

    @NotEmpty
    private UUID orderUuid;

    @Positive
    private Double totalAmount;

    private Status orderStatus;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;

    private Address address;

    private UserDto userDto;

    private List<OrderRequest> orderRequests;

    private UUID paymentId;
}
