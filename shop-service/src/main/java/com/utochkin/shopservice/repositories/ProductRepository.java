package com.utochkin.shopservice.repositories;

import com.utochkin.shopservice.models.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.articleId IN :articleIds")
    List<Product> findAllByArticleIds(@Param("articleIds") List<UUID> articleIds);

    Optional <Product> findByArticleId(UUID articleId);

    void deleteByArticleId(UUID articleId);

    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity - :quantity " +
            "WHERE p.articleId = :articleId AND p.quantity >= :quantity")
    int deductStock(@Param("articleId") UUID articleId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity + :quantity " +
            "WHERE p.articleId = :articleId")
    int restoreStock(@Param("articleId") UUID articleId, @Param("quantity") int quantity);
}
