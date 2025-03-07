package com.utochkin.notificationservice.models;

import java.util.UUID;

public record OrderRequest(UUID articleId, Integer quantity) {

}
