package com.utochkin.orderservice.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Позиция заказа в запросе клиента: articleId + количество. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {

    @NotNull
    @Schema(description = "UUID артикула", example = "3873f81b-6d10-4860-97f4-0719eb88afaa")
    private UUID articleId;

    @NotNull
    @Positive
    @Schema(description = "Количество", example = "1")
    private Integer quantity;

}
