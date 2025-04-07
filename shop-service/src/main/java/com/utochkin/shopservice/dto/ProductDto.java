package com.utochkin.shopservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Товар в магазине")
public class ProductDto implements Serializable {
    @Schema(description = "UUID товара", example = "3873f81b-6d10-4860-97f4-0719eb88afaa", type = "string", format = "uuid")
    @NotEmpty
    private UUID articleId;

    @Schema(description = "Название товара", example = "Телефон", type = "string")
    @NotEmpty
    private String name;

    @Schema(description = "Количество товара в магазине", example = "14", type = "integer")
    @Positive
    private Integer quantity;

    @Schema(description = "Стоимость товара в магазине", example = "1000.0", type = "number", format = "double")
    @Positive
    private Double price;
}
