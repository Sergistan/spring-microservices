package com.utochkin.orderservice.models;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Builder
@Table(name = "address")
public class Address implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "ID адреса", example = "29")
    private Long id;

    @Column(name = "city", unique = true)
    @Schema(description = "Город", example = "Los Angeles")
    @NotEmpty
    private String city;

    @Column(name = "street", unique = true)
    @Schema(description = "Улица", example = "33th Avenue")
    @NotEmpty
    private String street;

    @Column(name = "house_number")
    @Schema(description = "Номер дома", example = "233")
    @Positive
    private Integer houseNumber;

    @Column(name = "apartment_number")
    @Schema(description = "Номер квартиры", example = "112")
    @Positive
    private Integer apartmentNumber;
}
