package com.utochkin.shopservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Товар в магазине")
public class ProductDtoRequest {

    @Schema(description = "Название товара", example = "Телефон", type = "string")
    @NotEmpty
    private String name;

    @Schema(description = "Количество товара в магазине", example = "14", type = "integer")
    @Positive
    private Integer quantity;

    @Schema(description = "Стоимость товара в магазине", example = "1000.0", type = "number", format = "double")
    @NotNull
    @Positive
    private Double price;
}
