package com.utochkin.paymentservice.services;


import com.utochkin.paymentservice.exceptions.CardNumberNotFoundException;
import com.utochkin.paymentservice.exceptions.FailedPayOrderException;
import com.utochkin.paymentservice.models.Account;
import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.repositories.AccountRepository;
import com.utochkin.paymentservice.requests.AccountRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Service
@RequiredArgsConstructor
@Log4j2
public class PaymentService {

    private final AccountRepository accountRepository;

    @Transactional
    public PaymentResponse paymentOrder(AccountRequest accountRequest) {
        log.info("PaymentService: попытка списания {} с карты {}", accountRequest.getTotalAmount(), accountRequest.getCardNumber());

        Double amountMoneyByCardNumber = accountRepository.findAmountMoneyByCardNumber(accountRequest.getCardNumber()).orElseThrow(FailedPayOrderException::new);
        Account accountByCardNumber = accountRepository.findAccountByCardNumber(accountRequest.getCardNumber()).orElseThrow(CardNumberNotFoundException::new);

        if (amountMoneyByCardNumber >= accountRequest.getTotalAmount()) {
            double changeAmountMoneyUserAfterPaymentOrder = amountMoneyByCardNumber - accountRequest.getTotalAmount();
            accountByCardNumber.setAmountMoney(changeAmountMoneyUserAfterPaymentOrder);
            accountRepository.save(accountByCardNumber);
            log.info("PaymentService: списание успешно. Новый баланс: {}", changeAmountMoneyUserAfterPaymentOrder);
            return new PaymentResponse(UUID.randomUUID(), Status.SUCCESS);
        } else {
            log.info("PaymentService: недостаточно средств для списания.");
            return new PaymentResponse(null, Status.FAILED);
        }
    }

    @Transactional
    public PaymentResponse refundedOrder(AccountRequest accountRequest) {
        log.info("PaymentService: возврат {} на карту {}", accountRequest.getTotalAmount(), accountRequest.getCardNumber());

        Double amountMoneyByCardNumber = accountRepository.findAmountMoneyByCardNumber(accountRequest.getCardNumber()).orElseThrow(FailedPayOrderException::new);
        Account accountByCardNumber = accountRepository.findAccountByCardNumber(accountRequest.getCardNumber()).orElseThrow(CardNumberNotFoundException::new);

        double changeAmountMoneyUserAfterRefundedOrder = amountMoneyByCardNumber + accountRequest.getTotalAmount();
        accountByCardNumber.setAmountMoney(changeAmountMoneyUserAfterRefundedOrder);
        accountRepository.save(accountByCardNumber);
        log.info("PaymentService: возврат успешно завершён. Новый баланс: {}", changeAmountMoneyUserAfterRefundedOrder);
        return new PaymentResponse(null, Status.REFUNDED);
    }
}

