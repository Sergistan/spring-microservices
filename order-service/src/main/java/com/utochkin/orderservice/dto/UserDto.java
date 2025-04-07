package com.utochkin.orderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserDto(@Schema(description = "Имя пользователя", example = "sergistan") String username,
                      @Schema(description = "Имя", example = "Сергей") String firstName,
                      @Schema(description = "Фамилия", example = "Уточкин") String lastName,
                      @Schema(description = "Электронная почта", example = "dzaga73i98@gmail.com") String email) {
}
