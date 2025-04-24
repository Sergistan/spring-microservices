package com.utochkin.orderservice.controllers;


import com.utochkin.orderservice.request.AccountRequest;
import com.utochkin.orderservice.request.PaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

//@FeignClient(name = "payment-service", url = "http://localhost:8072/payment/api/v1")
@FeignClient(name = "payment-service", url = "http://getaway-server:8072/payment/api/v1")
public interface PaymentController {

    @PostMapping("/pay")
    PaymentResponse paymentOrder(@RequestBody AccountRequest accountRequest);

    @PostMapping("/refunded")
    PaymentResponse refundedOrder(@RequestBody AccountRequest accountRequest);

}
