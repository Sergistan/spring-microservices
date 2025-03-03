package com.utochkin.paymentservice.repositories;

import com.utochkin.paymentservice.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("SELECT o.amountMoney FROM Account o WHERE o.cardNumber = :cardNumber")
    Optional<Double> findAmountMoneyByCardNumber(@Param("cardNumber") String cardNumber);

    Optional<Account> findAccountByCardNumber (String cardNumber);
}
