package com.utochkin.deliveryservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Копия контракта события order-service (topic-orders) на стороне консьюмера.
 * Тип-маппинг пакетов задан в config-repo: spring.json.type.mapping
 * com.utochkin.orderservice.dto.OrderDtoForKafka -> com.utochkin.deliveryservice.dto.OrderDtoForKafka.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDtoForKafka implements Serializable {

    @NotEmpty
    private UUID orderUuid;

    @Positive
    private BigDecimal totalAmount;

    private Status orderStatus;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;

    private AddressDto addressDto;

    private UserDto userDto;

    private List<OrderRequest> orderRequests;

    private UUID paymentId;

    private UUID eventId;
}
