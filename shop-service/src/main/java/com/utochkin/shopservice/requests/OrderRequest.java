package com.utochkin.shopservice.requests;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class OrderRequest {
    @NotEmpty
    private UUID articleId;

    @Positive
    private Integer quantity;

}
