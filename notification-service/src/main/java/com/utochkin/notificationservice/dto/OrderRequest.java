package com.utochkin.notificationservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/** Позиция заказа из события (для списка товаров в письме). */
public record OrderRequest(@NotEmpty UUID articleId,
                           @Positive Integer quantity) {

}
