package com.utochkin.paymentservice.repositories;

import com.utochkin.paymentservice.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findAccountByCardNumber(String cardNumber);

    @Modifying
    @Query("UPDATE Account a SET a.amountMoney = a.amountMoney - :amount " +
            "WHERE a.cardNumber = :cardNumber AND a.amountMoney >= :amount")
    int withdraw(@Param("cardNumber") String cardNumber, @Param("amount") double amount);

    @Modifying
    @Query("UPDATE Account a SET a.amountMoney = a.amountMoney + :amount " +
            "WHERE a.cardNumber = :cardNumber")
    int deposit(@Param("cardNumber") String cardNumber, @Param("amount") double amount);
}

