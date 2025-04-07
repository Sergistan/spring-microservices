package com.utochkin.historyservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.utochkin.historyservice.models.Address;
import com.utochkin.historyservice.models.OrderRequest;
import com.utochkin.historyservice.models.Status;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderDtoForKafka implements Serializable {
    @Field(targetType = FieldType.STRING)
    private UUID orderUuid;

    @Positive
    private Double totalAmount;

    private Status orderStatus;

    @Field(targetType = FieldType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;

    @Field(targetType = FieldType.STRING)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;

    private Address address;

    private UserDto userDto;

    private List<OrderRequest> orderRequests;

    @Field(targetType = FieldType.STRING)
    private UUID paymentId;
}
