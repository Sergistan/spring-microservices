package com.utochkin.paymentservice.dto;

import java.io.Serializable;
import java.util.UUID;

/** Ответ GET /payments/{orderUuid}: флаги paid/refunded + paymentId (для сверки ReconciliationJob order-service). */
public record OrderPaymentsResponse(UUID orderUuid, boolean paid, boolean refunded, UUID paymentId) implements Serializable {
}
