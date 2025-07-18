package com.utochkin.orderservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.orderservice.config.Config;
import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.exceptions.OrderNotFoundException;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.models.User;
import com.utochkin.orderservice.request.CompositeRequest;
import com.utochkin.orderservice.request.OrderRequest;
import com.utochkin.orderservice.request.PaymentRequest;
import com.utochkin.orderservice.request.PaymentResponse;
import com.utochkin.orderservice.services.OrderService;
import com.utochkin.orderservice.services.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class)
@Import(Config.class)
@ActiveProfiles("test")
public class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final UUID orderUuid = UUID.randomUUID();

    @Test
    @DisplayName("POST /order/api/v1/create — 201, новый пользователь и заказ")
    void createOrder_NewUser_Returns201() throws Exception {
        List<OrderRequest> requests = List.of(
                new OrderRequest(UUID.randomUUID(), 2),
                new OrderRequest(UUID.randomUUID(), 1)
        );
        AddressDto address = new AddressDto("LA", "Main St", 10, 5);
        CompositeRequest compositeRequest = new CompositeRequest(requests, address);

        given(userService.isUserExistsByUsername("sub", "login")).willReturn(false);
        User createdUser = User.builder()
                .subId("sub").username("login").firstName("John").lastName("Doe").build();
        given(userService.createUser(eq("sub"), eq("login"), any(), any(), any(), any()))
                .willReturn(createdUser);

        given(orderService.checkOrder(requests)).willReturn(true);

        OrderDto returned = new OrderDto(
                UUID.randomUUID(), 300.0, /* status */ null, /*createdAt*/null,
                address, /*userDto*/null, requests
        );
        given(orderService.createOrder(eq(createdUser), eq(requests), eq(address)))
                .willReturn(returned);

        mvc.perform(post("/order/api/v1/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        // эмулируем заголовки из JwtTokenFilter
                        .header("X-User-SubId", "sub")
                        .header("X-User-UserName", "login")
                        .header("X-User-FirstName", "John")
                        .header("X-User-LastName", "Doe")
                        .header("X-User-Email", "john@doe.com")
                        .header("X-User-Role", "USER")
                        .content(mapper.writeValueAsString(compositeRequest))
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderUuid").value(returned.orderUuid().toString()))
                .andExpect(jsonPath("$.totalAmount").value(300.0));
    }

    @Test
    @DisplayName("POST /order/api/v1/create — 400, товар не найден")
    void createOrder_InvalidRequest_Returns400() throws Exception {
        List<OrderRequest> requests = List.of(new OrderRequest(UUID.randomUUID(), 1000));
        CompositeRequest cr = new CompositeRequest(requests,
                new AddressDto("LA","St",1,1)
        );

        given(orderService.checkOrder(requests)).willReturn(false);

        mvc.perform(post("/order/api/v1/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-SubId", "sub")
                        .header("X-User-UserName", "login")
                        .header("X-User-FirstName", "John")
                        .header("X-User-LastName",  "Doe")
                        .header("X-User-Email",     "john@doe.com")
                        .header("X-User-Role",      "USER")
                        .content(mapper.writeValueAsString(cr))
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().string("У нас нет товара в таком количестве или вы неправильно задали артикул товара"));
    }


    @Test
    @DisplayName("POST /order/api/v1/pay — 200, успешная оплата")
    void paymentOrder_Success() throws Exception {
        PaymentRequest req = new PaymentRequest(orderUuid, "1234 5678 9012 3456");
        PaymentResponse resp = new PaymentResponse(UUID.randomUUID(), Status.SUCCESS);

        given(orderService.paymentOrder(req)).willReturn(resp);

        mvc.perform(post("/order/api/v1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(resp.getPaymentId().toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /order/api/v1/pay — 404, заказ не найден")
    void paymentOrder_NotFound() throws Exception {
        PaymentRequest req = new PaymentRequest(orderUuid, "1234 5678 9012 3456");
        given(orderService.paymentOrder(req)).willThrow(new OrderNotFoundException());

        mvc.perform(post("/order/api/v1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /order/api/v1/refunded — 200, успешный возврат")
    void refundedOrder_Success() throws Exception {
        PaymentRequest req = new PaymentRequest(orderUuid, "1234 5678 9012 3456");

        mvc.perform(post("/order/api/v1/refunded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(content().string("Заказ успешно отменен"));
    }

    @Test
    @DisplayName("POST /order/api/v1/refunded — 404, заказ не найден")
    void refundedOrder_NotFound() throws Exception {
        PaymentRequest req = new PaymentRequest(orderUuid, "1234 5678 9012 3456");
        willThrow(new OrderNotFoundException())
                .given(orderService).refundedOrder(req);

        mvc.perform(post("/order/api/v1/refunded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                )
                .andExpect(status().isNotFound());
    }

}
