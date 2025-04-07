package com.utochkin.historyservice.models;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.UUID;

public record OrderRequest(@Field(targetType = FieldType.STRING) @NotEmpty UUID articleId,
                          @Positive Integer quantity) {

}
