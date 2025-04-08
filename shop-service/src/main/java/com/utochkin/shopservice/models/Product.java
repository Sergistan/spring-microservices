package com.utochkin.shopservice.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Builder
@Table(name = "products")
public class Product implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false, columnDefinition = "UUID", unique = true)
    @NotEmpty
    private UUID articleId;

    @Column(name = "name", unique = true)
    @NotEmpty
    private String name;

    @Column(name = "quantity")
    @Positive
    private Integer quantity;

    @Column(name = "price")
    @NotNull
    @Positive
    private Double price;
}
