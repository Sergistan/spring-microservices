package com.utochkin.orderservice.feign;

import com.utochkin.orderservice.web.OrderRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Тело команды изменения остатков в shop-service (Feign): orderUuid — ключ идемпотентности
 * (маркер stock_operations), orderRequests — позиции списания/восстановления.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockChangeRequest {
    private UUID orderUuid;
    private List<OrderRequest> orderRequests;
}
