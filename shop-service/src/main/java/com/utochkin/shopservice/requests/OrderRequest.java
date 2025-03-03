package com.utochkin.shopservice.requests;

import lombok.Data;

import java.util.UUID;

@Data
public class OrderRequest {

    private UUID articleId;
    private Integer quantity;

}
