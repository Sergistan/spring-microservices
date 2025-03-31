package com.utochkin.historyservice.repositories;

import com.utochkin.historyservice.models.OrderHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderHistoryRepository extends MongoRepository<OrderHistory, String> {

    Optional<OrderHistory> findByUsername (String username);
}
