package com.utochkin.historyservice.models;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.util.UUID;

public record OrderRequest(@Field(targetType = FieldType.STRING) UUID articleId,
                           Integer quantity) {

}
