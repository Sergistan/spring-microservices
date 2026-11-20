package com.utochkin.deliveryservice.dto;

/** Адрес доставки заказа из события. */
public record AddressDto(
        String city,
        String street,
        Integer houseNumber,
        Integer apartmentNumber
) {
}
