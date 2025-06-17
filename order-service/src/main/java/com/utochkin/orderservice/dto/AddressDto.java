package com.utochkin.orderservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Адрес доставки заказа")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AddressDto {
    @Schema(description = "Название города", example = "Los Angeles", type = "string")
    private String city;
    @Schema(description = "Название улицы", example = "33th Avenue", type = "string")
    private String street;
    @Schema(description = "Номер дома", example = "233", type = "integer")
    private Integer houseNumber;
    @Schema(description = "Номер квартиры", example = "112", type = "integer")
    private Integer apartmentNumber;
}



