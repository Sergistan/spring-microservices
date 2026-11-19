package com.utochkin.orderservice.controllers;


import com.utochkin.orderservice.feign.AccountRequest;
import com.utochkin.orderservice.feign.OrderPaymentsResponse;
import com.utochkin.orderservice.feign.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/**
 * Feign-клиент к payment-service (напрямую через Eureka, мимо gateway).
 * pay/refunded — оплата и возврат из OrderService (снабжаются Authorization перехватчиком),
 * getOrderPayments — сверка платежей заказа для ReconciliationJob (сервисный токен).
 */
//@FeignClient(name = "payment-service", url = "http://localhost:8072/payment/api/v1")
@FeignClient(name = "payment-service", path = "/payment/api/v1")
public interface PaymentController {

    /** POST /pay — оплата (идемпотентно по orderUuid). */
    @PostMapping("/pay")
    PaymentResponse paymentOrder(@RequestBody AccountRequest accountRequest);

    /** POST /refunded — возврат средств. */
    @PostMapping("/refunded")
    PaymentResponse refundedOrder(@RequestBody AccountRequest accountRequest);

    /** GET /{orderUuid} — сводка платежей (использует ReconciliationJob). */
    @GetMapping("/{orderUuid}")
    OrderPaymentsResponse getOrderPayments(@PathVariable("orderUuid") UUID orderUuid);

}
