package com.utochkin.orderservice.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {

    @NotEmpty
    @Schema(description = "UUID артикула", example = "3873f81b-6d10-4860-97f4-0719eb88afaa")
    private UUID articleId;

    @NotEmpty
    @Schema(description = "Количество", example = "1")
    private Integer quantity;

}
