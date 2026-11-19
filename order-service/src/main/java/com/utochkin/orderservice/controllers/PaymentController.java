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

//@FeignClient(name = "payment-service", url = "http://localhost:8072/payment/api/v1")
@FeignClient(name = "payment-service", path = "/payment/api/v1")
public interface PaymentController {

    @PostMapping("/pay")
    PaymentResponse paymentOrder(@RequestBody AccountRequest accountRequest);

    @PostMapping("/refunded")
    PaymentResponse refundedOrder(@RequestBody AccountRequest accountRequest);

    @GetMapping("/{orderUuid}")
    OrderPaymentsResponse getOrderPayments(@PathVariable("orderUuid") UUID orderUuid);

}
