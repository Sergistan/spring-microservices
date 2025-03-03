package com.utochkin.orderservice.controllers;

import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.models.User;
import com.utochkin.orderservice.request.CompositeRequest;
import com.utochkin.orderservice.request.PaymentRequest;
import com.utochkin.orderservice.services.OrderService;
import com.utochkin.orderservice.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/order/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final UserService userService;
    private final OrderService orderService;



    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestHeader("X-User-SubId") String subId,
                                              @RequestHeader("X-User-UserName") String username,
                                              @RequestHeader("X-User-FirstName") String firstName,
                                              @RequestHeader("X-User-LastName") String lastName,
                                              @RequestHeader("X-User-Email") String email,
                                              @RequestHeader("X-User-Role") String role,
                                              @RequestBody CompositeRequest compositeRequest
    ) {

        firstName = URLDecoder.decode(firstName, StandardCharsets.UTF_8);
        lastName = URLDecoder.decode(lastName, StandardCharsets.UTF_8);
        User user;

        if (!userService.isUserExistsByUsername(subId, username)) {
            user = userService.createUser(subId, username, firstName, lastName, email, role);
        } else {
            user = userService.findUserBySubIdAndUsername(subId, username);
        }

        if (!orderService.checkOrder(compositeRequest.getOrderRequests())) {
            return new ResponseEntity<>("У нас нет товара в таком количестве или вы неправильно задали артикул товара", HttpStatus.BAD_REQUEST);
        } else {
            OrderDto orderDto = orderService.createOrder(user, compositeRequest.getOrderRequests(), compositeRequest.getAddress());
            return new ResponseEntity<>(orderDto, HttpStatus.CREATED);
        }
    }

    @PostMapping("/pay")
    public ResponseEntity<?> paymentOrder(@RequestBody PaymentRequest paymentRequest) {
        return new ResponseEntity<>(orderService.paymentOrder(paymentRequest), HttpStatus.OK);
    }

    @PostMapping("/refunded")
    public ResponseEntity<?> refundedOrder(@RequestBody PaymentRequest paymentRequest) {
        orderService.refundedOrder(paymentRequest);
        return new ResponseEntity<>("Заказ успешно отменен", HttpStatus.OK);
    }

}
