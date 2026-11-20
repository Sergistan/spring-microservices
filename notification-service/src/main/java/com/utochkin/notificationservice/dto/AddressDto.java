package com.utochkin.notificationservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

/** Адрес доставки заказа из события (для текста письма). */
public record AddressDto(
        @NotEmpty String city,
        @NotEmpty String street,
        @Positive Integer houseNumber,
        @Positive Integer apartmentNumber
) {
}

