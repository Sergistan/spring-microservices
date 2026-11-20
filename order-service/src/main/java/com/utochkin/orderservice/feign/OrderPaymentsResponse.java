package com.utochkin.orderservice.feign;

import java.io.Serializable;
import java.util.UUID;

/** Ответ GET /payments/{orderUuid}: флаги paid/refunded + paymentId — сверка ReconciliationJob. */
public record OrderPaymentsResponse(UUID orderUuid, boolean paid, boolean refunded, UUID paymentId) implements Serializable {
}
