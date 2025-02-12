package com.utochkin.shopservice.dto;

import java.util.UUID;

public record ProductDto(UUID articleId,
                         String name,
                         Integer quantity,
                         Double price) {
}
