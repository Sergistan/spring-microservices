package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsBySubIdAndUsername(String subId, String username);

    Optional<User> findBySubIdAndUsername(String subId, String username);
}
