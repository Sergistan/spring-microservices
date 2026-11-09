package com.utochkin.paymentservice.models;

import java.io.Serializable;
import java.util.UUID;

public record OrderPaymentsResponse(UUID orderUuid, boolean paid, boolean refunded, UUID paymentId) implements Serializable {
}
