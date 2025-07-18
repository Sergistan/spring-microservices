package com.utochkin.paymentservice.services;


import com.utochkin.paymentservice.exceptions.CardNumberNotFoundException;
import com.utochkin.paymentservice.models.Account;
import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.repositories.AccountRepository;
import com.utochkin.paymentservice.requests.AccountRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Log4j2
public class PaymentService {

    private final AccountRepository accountRepository;

    @Transactional
    public PaymentResponse paymentOrder(AccountRequest accountRequest) {
        log.info("PaymentService: попытка списания {} с карты {}",
                accountRequest.getTotalAmount(), accountRequest.getCardNumber());

        Account account = accountRepository.findAccountByCardNumber(accountRequest.getCardNumber())
                .orElseThrow(() -> {
                    log.error("PaymentService: не найден аккаунт с номером карты {}", accountRequest.getCardNumber());
                    return new CardNumberNotFoundException();
                });

        double currentBalance = account.getAmountMoney();
        if (currentBalance < accountRequest.getTotalAmount()) {
            log.info("PaymentService: недостаточно средств для списания.");
            return new PaymentResponse(null, Status.FAILED);
        }

        double newBalance = currentBalance - accountRequest.getTotalAmount();
        BigDecimal bd = BigDecimal.valueOf(newBalance).setScale(2, RoundingMode.HALF_UP);
        account.setAmountMoney(bd.doubleValue());
        accountRepository.save(account);
        log.info("PaymentService: списание успешно. Новый баланс: {}", bd.doubleValue());

        return new PaymentResponse(UUID.randomUUID(), Status.SUCCESS);
    }

    @Transactional
    public PaymentResponse refundedOrder(AccountRequest accountRequest) {
        log.info("PaymentService: возврат {} на карту {}", accountRequest.getTotalAmount(), accountRequest.getCardNumber());

        Account account = accountRepository.findAccountByCardNumber(accountRequest.getCardNumber())
                .orElseThrow(() -> {
                    log.error("PaymentService: не найден аккаунт для возврата с номером карты {}", accountRequest.getCardNumber());
                    return new CardNumberNotFoundException();
                });

        double newBalance = account.getAmountMoney() + accountRequest.getTotalAmount();
        BigDecimal bd = BigDecimal.valueOf(newBalance).setScale(2, RoundingMode.HALF_UP);
        account.setAmountMoney(bd.doubleValue());
        accountRepository.save(account);

        log.info("PaymentService: возврат успешно завершён. Новый баланс: {}", bd.doubleValue());
        return new PaymentResponse(null, Status.REFUNDED);
    }
}

