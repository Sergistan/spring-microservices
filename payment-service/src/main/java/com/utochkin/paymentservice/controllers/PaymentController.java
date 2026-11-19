package com.utochkin.paymentservice.controllers;

import com.utochkin.paymentservice.dto.OrderPaymentsResponse;
import com.utochkin.paymentservice.dto.PaymentResponse;
import com.utochkin.paymentservice.web.AccountRequest;
import com.utochkin.paymentservice.services.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST API платежей. Основной потребитель — order-service НАПРЯМУЮ через Eureka (мимо gateway):
 * POST /pay и /refunded — оплата/возврат заказа (Authorization = JWT пользователя или сервисный токен),
 * GET /{orderUuid} — сверка платежей для ReconciliationJob. Внешний контур закрыт на getaway-server.
 */
@RestController
@RequestMapping("/payment/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** POST /pay: идемпотентная оплата заказа (вызывает order-service через Eureka). */
    @PostMapping("/pay")
    PaymentResponse paymentOrder(@RequestBody @Valid AccountRequest accountRequest){
        return paymentService.paymentOrder(accountRequest);
    }

    /** POST /refunded: идемпотентный возврат средств. */
    @PostMapping("/refunded")
    PaymentResponse refundedOrder(@RequestBody @Valid AccountRequest accountRequest){
        return paymentService.refundedOrder(accountRequest);
    }

    /** GET /{orderUuid}: сводка платежей для ReconciliationJob (order-service). */
    @GetMapping("/{orderUuid}")
    OrderPaymentsResponse getOrderPayments(@PathVariable("orderUuid") UUID orderUuid) {
        return paymentService.getOrderPayments(orderUuid);
    }

}
