package com.utochkin.deliveryservice.dto;

import java.util.UUID;

/** Позиция заказа из события. */
public record OrderRequest(UUID articleId, Integer quantity) {
}
