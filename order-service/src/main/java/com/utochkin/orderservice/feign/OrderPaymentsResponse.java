package com.utochkin.orderservice.feign;

import java.io.Serializable;
import java.util.UUID;

public record OrderPaymentsResponse(UUID orderUuid, boolean paid, boolean refunded, UUID paymentId) implements Serializable {
}
