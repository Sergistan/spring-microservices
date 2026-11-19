package com.utochkin.orderservice.feign;

import com.utochkin.orderservice.web.OrderRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockChangeRequest {
    private UUID orderUuid;
    private List<OrderRequest> orderRequests;
}
