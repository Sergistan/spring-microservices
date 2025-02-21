package com.utochkin.shopservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDto {
    private UUID articleId;
    private String name;
    private Integer quantity;
    private Double price;
}
