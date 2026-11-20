package com.utochkin.historyservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.UUID;

/** Позиция заказа из события. */
public record OrderRequest(@Field(targetType = FieldType.STRING) @NotEmpty UUID articleId,
                          @Positive Integer quantity) {

}
