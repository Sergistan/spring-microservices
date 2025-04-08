package com.utochkin.orderservice.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
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
@Table(name = "product_infos")
public class ProductInfo implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "article_id", unique = true)
  @NotEmpty
  private UUID articleId;

  @Column(name = "quantity")
  @Positive
  private Integer quantity;

  @ManyToOne
  @JoinColumn(name = "order_id")
  private Order order;
}
