package com.utochkin.orderservice.request;

import lombok.Data;

import java.util.UUID;

@Data
public class OrderRequest {

    private UUID articleId;
    private Integer quantity;

}
