package com.utochkin.paymentservice.repositories;

import com.utochkin.paymentservice.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findAccountByCardNumber(String cardNumber);
}
