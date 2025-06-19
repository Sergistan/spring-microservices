package com.utochkin.paymentservice.services;


import com.utochkin.paymentservice.exceptions.CardNumberNotFoundException;
import com.utochkin.paymentservice.exceptions.FailedPayOrderException;
import com.utochkin.paymentservice.models.Account;
import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.models.Status;
import com.utochkin.paymentservice.repositories.AccountRepository;
import com.utochkin.paymentservice.requests.AccountRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Service
@RequiredArgsConstructor
public class PaymentService {

    private final AccountRepository accountRepository;

    @Transactional
    public PaymentResponse paymentOrder(AccountRequest accountRequest) {
        Double amountMoneyByCardNumber = accountRepository.findAmountMoneyByCardNumber(accountRequest.getCardNumber()).orElseThrow(FailedPayOrderException::new);
        Account accountByCardNumber = accountRepository.findAccountByCardNumber(accountRequest.getCardNumber()).orElseThrow(CardNumberNotFoundException::new);

        if (amountMoneyByCardNumber >= accountRequest.getTotalAmount()) {
            double changeAmountMoneyUserAfterPaymentOrder = amountMoneyByCardNumber - accountRequest.getTotalAmount();
            accountByCardNumber.setAmountMoney(changeAmountMoneyUserAfterPaymentOrder);
            accountRepository.save(accountByCardNumber);
            return new PaymentResponse(UUID.randomUUID(), Status.SUCCESS);
        } else {
            return new PaymentResponse(null, Status.FAILED);
        }
    }

    @Transactional
    public PaymentResponse refundedOrder(AccountRequest accountRequest) {
        Double amountMoneyByCardNumber = accountRepository.findAmountMoneyByCardNumber(accountRequest.getCardNumber()).orElseThrow(FailedPayOrderException::new);
        Account accountByCardNumber = accountRepository.findAccountByCardNumber(accountRequest.getCardNumber()).orElseThrow(CardNumberNotFoundException::new);

            double changeAmountMoneyUserAfterRefundedOrder = amountMoneyByCardNumber + accountRequest.getTotalAmount();
            accountByCardNumber.setAmountMoney(changeAmountMoneyUserAfterRefundedOrder);
            accountRepository.save(accountByCardNumber);
            return new PaymentResponse(null, Status.REFUNDED);
    }
}

