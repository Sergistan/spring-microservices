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


/**
 * Доступ к товарам + атомарные операции остатков для заказов.
 * deductStock/restoreStock — @Modifying UPDATE в одну строку с условием quantity >= :quantity
 * (списание конкурентно и не уходит в минус); возврат 0 строк означает нехватку товара.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Товары по списку articleId одним запросом (IN) — для checkOrder/суммы заказа. */
    @Query("SELECT p FROM Product p WHERE p.articleId IN :articleIds")
    List<Product> findAllByArticleIds(@Param("articleIds") List<UUID> articleIds);

    /** Поиск по articleId (бизнес-ключ товара из заказов). */
    Optional <Product> findByArticleId(UUID articleId);

    /** Удаление по articleId. */
    void deleteByArticleId(UUID articleId);

    /** Атомарное списание: 1 строка = успех, 0 = не хватило остатка (минус невозможен). */
    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity - :quantity " +
            "WHERE p.articleId = :articleId AND p.quantity >= :quantity")
    int deductStock(@Param("articleId") UUID articleId, @Param("quantity") int quantity);

    /** Возврат остатка (безусловно прибавляем). */
    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity + :quantity " +
            "WHERE p.articleId = :articleId")
    int restoreStock(@Param("articleId") UUID articleId, @Param("quantity") int quantity);
}
