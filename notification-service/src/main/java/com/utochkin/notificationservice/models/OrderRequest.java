package com.utochkin.notificationservice.models;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderRequest(@NotEmpty UUID articleId,
                           @Positive Integer quantity) {

}
