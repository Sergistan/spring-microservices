package com.utochkin.orderservice.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderRequest {

    private UUID articleId;
    private Integer quantity;

}
