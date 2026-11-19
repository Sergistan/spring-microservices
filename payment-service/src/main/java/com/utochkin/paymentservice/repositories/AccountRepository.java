package com.utochkin.paymentservice.repositories;

import com.utochkin.paymentservice.models.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
/**
 * Карт-счета: атомарные withdraw/deposit с защитой от минуса (условие в WHERE).
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    /** Счёт по номеру карты (404 из сервиса, если не найден). */
    Optional<Account> findAccountByCardNumber(String cardNumber);

    /** Атомарное списание с карты: 0 строк = недостаточно средств (условие в WHERE). */
    @Modifying
    @Query("UPDATE Account a SET a.amountMoney = a.amountMoney - :amount " +
            "WHERE a.cardNumber = :cardNumber AND a.amountMoney >= :amount")
    int withdraw(@Param("cardNumber") String cardNumber, @Param("amount") BigDecimal amount);

    /** Зачисление на карту (безусловное прибавление). */
    @Modifying
    @Query("UPDATE Account a SET a.amountMoney = a.amountMoney + :amount " +
            "WHERE a.cardNumber = :cardNumber")
    int deposit(@Param("cardNumber") String cardNumber, @Param("amount") BigDecimal amount);
}

