package com.utochkin.shopservice.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import org.hibernate.annotations.Check;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Builder
@Check(constraints = "quantity >= 0")
@Table(name = "products")
public class Product implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false, columnDefinition = "UUID")
    @NotNull
    private UUID articleId;

    @Column(name = "name")
    @NotEmpty
    private String name;

    @Column(name = "quantity")
    @NotNull
    @Positive
    private Integer quantity;

    @Column(name = "price", precision = 19, scale = 2)
    @NotNull
    @Positive
    private BigDecimal price;
}
