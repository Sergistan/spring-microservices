package com.utochkin.notificationservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;

public record UserDto(@NotEmpty String username,
                      @NotEmpty String firstName,
                      @NotEmpty String lastName,
                      @Email String email) {
}
