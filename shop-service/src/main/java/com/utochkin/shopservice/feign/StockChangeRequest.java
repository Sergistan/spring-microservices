package com.utochkin.shopservice.feign;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Команда изменения остатков от order-service: orderUuid — ключ идемпотентности, позиции — что менять. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockChangeRequest {
    @NotNull
    private UUID orderUuid;

    @NotEmpty
    private List<@Valid OrderRequest> orderRequests;
}
