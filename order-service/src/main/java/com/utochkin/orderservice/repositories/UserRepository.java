package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Локальные пользователи order-service (зеркало Keycloak из заголовков X-User-*).
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Существует ли пользователь (false -> первый заказ, будет создан). */
    boolean existsBySubIdAndUsername(String subId, String username);

    /** Поиск по паре subId+username (Keycloak sub + preferred_username). */
    Optional<User> findBySubIdAndUsername(String subId, String username);
}
