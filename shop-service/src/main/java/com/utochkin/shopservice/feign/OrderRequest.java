package com.utochkin.shopservice.feign;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Позиция заказа во внутренних командах order-service (проверка остатков/списание/восстановление). */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {
    @NotNull
    private UUID articleId;

    @NotNull
    @Positive
    private Integer quantity;

}
