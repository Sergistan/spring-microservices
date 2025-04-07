package com.utochkin.historyservice.models;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record Address(@NotEmpty String city,
                      @NotEmpty String street,
                      @Positive Integer houseNumber,
                      @Positive Integer apartmentNumber) {

}
