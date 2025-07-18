package com.utochkin.notificationservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record AddressDto(
        @NotEmpty String city,
        @NotEmpty String street,
        @Positive Integer houseNumber,
        @Positive Integer apartmentNumber
) {
}

