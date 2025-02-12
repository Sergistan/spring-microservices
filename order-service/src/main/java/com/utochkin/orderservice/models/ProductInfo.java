package com.utochkin.orderservice.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Builder
@Table(name = "product_infos")
public class ProductInfo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "article_id")
  private UUID articleId;

  @Column(name = "quantity")
  private Integer quantity;

  @ManyToOne
  @JoinColumn(name = "order_id")
  private Order order;

}
