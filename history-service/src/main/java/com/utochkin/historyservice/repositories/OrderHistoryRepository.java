package com.utochkin.historyservice.repositories;

import com.utochkin.historyservice.models.OrderHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
/**
 * MongoDB-история заказов: один документ на пользователя, события в массиве orders.
 */
public interface OrderHistoryRepository extends MongoRepository<OrderHistory, String> {

    /** Документ истории пользователя по username (один документ на пользователя, события в массиве). */
    Optional<OrderHistory> findByUsername (String username);
}
