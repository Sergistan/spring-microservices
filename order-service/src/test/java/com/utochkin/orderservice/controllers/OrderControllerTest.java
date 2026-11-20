package com.utochkin.orderservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.orderservice.config.Config;
import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.dto.DeliveryStatusResponse;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.exceptions.DeliveryConflictException;
import com.utochkin.orderservice.exceptions.OrderNotFoundException;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.models.User;
import com.utochkin.orderservice.web.CardRequest;
import com.utochkin.orderservice.web.CompositeRequest;
import com.utochkin.orderservice.web.OrderRequest;
import com.utochkin.orderservice.web.PaymentRequest;
import com.utochkin.orderservice.feign.PaymentResponse;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;

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
    @DisplayName("POST /order/api/v1/orders — 201, новый пользователь и заказ")
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

        OrderDto returned = new OrderDto(UUID.randomUUID(), BigDecimal.valueOf(300.0), /* status */ null, /*createdAt*/null,
                address, /*userDto*/null, requests
        );
        given(orderService.createOrder(eq(createdUser), any(UUID.class), eq(requests), eq(address)))
                .willReturn(returned);

        mvc.perform(post("/order/api/v1/orders")
                        .with(jwt())
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
    @DisplayName("POST /order/api/v1/orders — 400, товар не найден")
    void createOrder_InvalidRequest_Returns400() throws Exception {
        List<OrderRequest> requests = List.of(new OrderRequest(UUID.randomUUID(), 1000));
        CompositeRequest cr = new CompositeRequest(requests,
                new AddressDto("LA","St",1,1)
        );

        given(orderService.checkOrder(requests)).willReturn(false);

        mvc.perform(post("/order/api/v1/orders")
                        .with(jwt())
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
    @DisplayName("POST /order/api/v1/orders/{uuid}/payments — 200, успешная оплата")
    void paymentOrder_Success() throws Exception {
        CardRequest req = new CardRequest("1234 5678 9012 3456");
        PaymentRequest expected = new PaymentRequest(orderUuid, "1234 5678 9012 3456");
        PaymentResponse resp = new PaymentResponse(UUID.randomUUID(), Status.SUCCESS);

        given(orderService.paymentOrder(expected, "sub")).willReturn(resp);

        mvc.perform(post("/order/api/v1/orders/{uuid}/payments", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(resp.getPaymentId().toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("POST /order/api/v1/orders/{uuid}/payments — 404, заказ не найден")
    void paymentOrder_NotFound() throws Exception {
        CardRequest req = new CardRequest("1234 5678 9012 3456");
        given(orderService.paymentOrder(new PaymentRequest(orderUuid, "1234 5678 9012 3456"), "sub"))
                .willThrow(new OrderNotFoundException());

        mvc.perform(post("/order/api/v1/orders/{uuid}/payments", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /order/api/v1/orders/{uuid}/payments — 403, заказ чужого пользователя")
    void paymentOrder_ForeignOwner_Forbidden() throws Exception {
        CardRequest req = new CardRequest("1234 5678 9012 3456");
        given(orderService.paymentOrder(new PaymentRequest(orderUuid, "1234 5678 9012 3456"), "sub"))
                .willThrow(new com.utochkin.orderservice.exceptions.OrderAccessDeniedException());

        mvc.perform(post("/order/api/v1/orders/{uuid}/payments", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageError").value("Ошибка: заказ принадлежит другому пользователю"));
    }

    @Test
    @DisplayName("POST /order/api/v1/orders/{uuid}/refunds — 200, успешный возврат")
    void refundedOrder_Success() throws Exception {
        CardRequest req = new CardRequest("1234 5678 9012 3456");

        mvc.perform(post("/order/api/v1/orders/{uuid}/refunds", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andExpect(content().string("Заказ успешно отменен"));
    }

    @Test
    @DisplayName("POST /order/api/v1/orders/{uuid}/refunds — 404, заказ не найден")
    void refundedOrder_NotFound() throws Exception {
        CardRequest req = new CardRequest("1234 5678 9012 3456");
        willThrow(new OrderNotFoundException())
                .given(orderService).refundedOrder(new PaymentRequest(orderUuid, "1234 5678 9012 3456"), "sub");

        mvc.perform(post("/order/api/v1/orders/{uuid}/refunds", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /order/api/v1/orders/{uuid}/deliveries — 200, сервисный токен delivery-service-client + ФИО курьера в теле")
    void startDelivery_ServiceClient_Returns200() throws Exception {
        given(orderService.markOrderDelivering(orderUuid, "Ivan Kurierov"))
                .willReturn(new DeliveryStatusResponse(orderUuid, Status.DELIVERING));

        mvc.perform(post("/order/api/v1/orders/{uuid}/deliveries", orderUuid)
                        .with(jwt().jwt(j -> j.claim("azp", "delivery-service-client")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierName\":\"Ivan Kurierov\"}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderUuid").value(orderUuid.toString()))
                .andExpect(jsonPath("$.orderStatus").value("DELIVERING"));
    }

    @Test
    @DisplayName("POST /order/api/v1/orders/{uuid}/deliveries — 403, обычный пользователь (azp не delivery)")
    void startDelivery_UserToken_Forbidden() throws Exception {
        mvc.perform(post("/order/api/v1/orders/{uuid}/deliveries", orderUuid)
                        .with(jwt().jwt(j -> j.claim("azp", "spring-microservices")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierName\":\"Ivan Kurierov\"}")
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageError").value("Ошибка: доступ разрешён только внутренним сервисам"));

        then(orderService).should(never()).markOrderDelivering(any(), any());
    }

    @Test
    @DisplayName("PATCH /order/api/v1/orders/{uuid}/deliveries — 409, возврат успел раньше доставки")
    void completeDelivery_Conflict() throws Exception {
        willThrow(new DeliveryConflictException(Status.REFUNDED))
                .given(orderService).markOrderDelivered(orderUuid);

        mvc.perform(patch("/order/api/v1/orders/{uuid}/deliveries", orderUuid)
                        .with(jwt().jwt(j -> j.claim("azp", "delivery-service-client")))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messageError").value("Заказ находится в статусе REFUNDED — операция доставки невозможна"));
    }

    @Test
    @DisplayName("PATCH /order/api/v1/orders/{uuid}/deliveries — 200, доставка завершена")
    void completeDelivery_Success() throws Exception {
        given(orderService.markOrderDelivered(orderUuid))
                .willReturn(new DeliveryStatusResponse(orderUuid, Status.DELIVERED));

        mvc.perform(patch("/order/api/v1/orders/{uuid}/deliveries", orderUuid)
                        .with(jwt().jwt(j -> j.claim("azp", "delivery-service-client")))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("DELIVERED"));
    }

    @Test
    @DisplayName("GET /order/api/v1/users/{subId}/has-active-orders — 200 true, сервисный токен auth-service-client")
    void hasActiveOrders_ServiceClient_Returns200() throws Exception {
        given(orderService.hasActiveOrders("sub-42")).willReturn(true);

        mvc.perform(get("/order/api/v1/users/{subId}/has-active-orders", "sub-42")
                        .with(jwt().jwt(j -> j.claim("azp", "auth-service-client")))
                )
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("GET /order/api/v1/users/{subId}/has-active-orders — 403, чужой клиент (azp)")
    void hasActiveOrders_WrongClient_Forbidden() throws Exception {
        mvc.perform(get("/order/api/v1/users/{subId}/has-active-orders", "sub-42")
                        .with(jwt().jwt(j -> j.claim("azp", "spring-microservices")))
                )
                .andExpect(status().isForbidden());

        then(orderService).should(never()).hasActiveOrders(any());
    }

}
