package com.utochkin.notificationservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderRequest(@NotEmpty UUID articleId,
                           @Positive Integer quantity) {

}
