package com.utochkin.shopservice.repositories;

import com.utochkin.shopservice.models.Product;
import feign.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
