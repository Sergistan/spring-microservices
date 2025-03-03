package com.utochkin.paymentservice.controllers;

import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.requests.AccountRequest;
import com.utochkin.paymentservice.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/pay")
    PaymentResponse payOrder(@RequestBody AccountRequest accountRequest){
        return paymentService.paymentOrder(accountRequest);
    }

    @PostMapping("/refunded")
    PaymentResponse refundedOrder(@RequestBody AccountRequest accountRequest){
        return paymentService.refundedOrder(accountRequest);
    }

}
