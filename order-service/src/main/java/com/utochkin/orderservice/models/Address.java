package com.utochkin.orderservice.models;

import jakarta.persistence.*;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Builder
@Table(name = "address")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city")
    private String city;

    @Column(name = "street")
    private String street    ;

    @Column(name = "house_number")
    private Integer houseNumber;

    @Column(name = "apartment_number")
    private Integer apartmentNumber;
}
