package com.utochkin.orderservice.controllers;

import com.utochkin.orderservice.dto.DeliveryStatusResponse;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.dto.ErrorResponse;
import com.utochkin.orderservice.exceptions.OrderAccessDeniedException;
import com.utochkin.orderservice.models.User;
import com.utochkin.orderservice.web.CardRequest;
import com.utochkin.orderservice.web.CompositeRequest;
import com.utochkin.orderservice.web.CourierRequest;
import com.utochkin.orderservice.web.PaymentRequest;
import com.utochkin.orderservice.feign.PaymentResponse;
import com.utochkin.orderservice.services.OrderService;
import com.utochkin.orderservice.services.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Входная точка REST API заказов (приходят через getaway-server, 8072 -> 8180).
 * Помимо JWT получает заголовки X-User-* (инжектирует JwtTokenFilter гейтвея):
 * POST /orders использует их для создания локального пользователя,
 * /orders/{id}/payments и /orders/{id}/refunds — для проверки владельца заказа (чужим 403).
 * Ответы: 201 + OrderDto при создании, PaymentResponse при оплате.
 */
@RestController
@RequestMapping("/order/api/v1")
@RequiredArgsConstructor
@Log4j2
@Tag(name = "Order endpoints", description = "Order API")
public class OrderController {

    private final UserService userService;
    private final OrderService orderService;

    /**
     * POST /orders: создание заказа. Порядок: URL-decode ФИО из заголовков -> найти/создать
     * локального пользователя по subId+username -> проверка остатков в shop ->
     * createOrder (STOCK_PENDING -> списание -> WAITING_FOR_PAYMENT). 201 + OrderDto.
     */
    @PostMapping("/orders")
    @Operation(summary = "Создание заказа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ оплачен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDtoForKafka.class))),
            @ApiResponse(responseCode = "201", description = "Заказ создан", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDto.class))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "402", description = "Ошибка оплаты заказа", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> createOrder(@Parameter(hidden = true) @RequestHeader(value = "X-User-SubId", required = false) String subId,
                                         @Parameter(hidden = true) @RequestHeader(value = "X-User-UserName", required = false) String username,
                                         @Parameter(hidden = true) @RequestHeader(value = "X-User-FirstName", required = false) String firstName,
                                         @Parameter(hidden = true) @RequestHeader(value = "X-User-LastName", required = false) String lastName,
                                         @Parameter(hidden = true) @RequestHeader(value = "X-User-Email", required = false) String email,
                                         @Parameter(hidden = true) @RequestHeader(value = "X-User-Role", required = false) String role,
                                         @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = CompositeRequest.class),
                                                 encoding = @Encoding(contentType = "application/json")),
                                                 description = "Ввод списка товаров для покупки и адреса доставки заказа", required = true) @Valid CompositeRequest compositeRequest
    ) {

        // Заголовки X-User-* приходит URL-encoded (кириллица ФИО) — декодируем
        firstName = URLDecoder.decode(firstName, StandardCharsets.UTF_8);
        lastName = URLDecoder.decode(lastName, StandardCharsets.UTF_8);
        User user;

        // Первый заказ пользователя -> создаём локальную запись из заголовков
        if (!userService.isUserExistsByUsername(subId, username)) {
            user = userService.createUser(subId, username, firstName, lastName, email, role);
        } else {
            user = userService.findUserBySubIdAndUsername(subId, username);
        }

        if (!orderService.checkOrder(compositeRequest.getOrderRequests())) {
            return new ResponseEntity<>("У нас нет товара в таком количестве или вы неправильно задали артикул товара", HttpStatus.BAD_REQUEST);
        } else {
            OrderDto orderDto = orderService.createOrder(user, UUID.randomUUID(), compositeRequest.getOrderRequests(), compositeRequest.getAddressDto());
            return new ResponseEntity<>(orderDto, HttpStatus.CREATED);
        }
    }

    /** POST /orders/{orderUuid}/payments: создание платежа — оплата заказа владельцем (subId из X-User-SubId; чужим 403, повторная оплата 400). */
    @PostMapping("/orders/{orderUuid}/payments")
    @Operation(summary = "Оплата заказа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ успешно оплачен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "402", description = "Ошибка оплаты заказа", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> paymentOrder(@Parameter(description = "UUID оплачиваемого заказа", example = "3f9edc4b-e4cf-4257-a485-72a147a0b45f", required = true) @PathVariable UUID orderUuid,
                                          @Parameter(hidden = true) @RequestHeader("X-User-SubId") String subId,
                                          @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = CardRequest.class),
            encoding = @Encoding(contentType = "application/json")),
            description = "Номер карты для оплаты заказа (orderUuid передаётся в пути)", required = true) @Valid CardRequest cardRequest) {
        return new ResponseEntity<>(orderService.paymentOrder(new PaymentRequest(orderUuid, cardRequest.getCardNumber()), subId), HttpStatus.OK);
    }

    /** POST /orders/{orderUuid}/refunds: создание возврата — отмена оплаченного заказа (только владелец; возврат средств и остатков). */
    @PostMapping("/orders/{orderUuid}/refunds")
    @Operation(summary = "Отмена заказа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ успешно отменен", content = @Content(schema = @Schema(implementation = String.class),
                    examples = @ExampleObject("Заказ успешно отменен"))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "402", description = "Ошибка оплаты заказа", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> refundedOrder(@Parameter(description = "UUID отменяемого заказа", example = "3f9edc4b-e4cf-4257-a485-72a147a0b45f", required = true) @PathVariable UUID orderUuid,
                                           @Parameter(hidden = true) @RequestHeader("X-User-SubId") String subId,
                                           @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = CardRequest.class),
            encoding = @Encoding(contentType = "application/json")),
            description = "Номер карты для возврата средств (orderUuid передаётся в пути)", required = true) @Valid CardRequest cardRequest) {
        orderService.refundedOrder(new PaymentRequest(orderUuid, cardRequest.getCardNumber()), subId);
        return new ResponseEntity<>("Заказ успешно отменен", HttpStatus.OK);
    }

    /**
     * POST /orders/{orderUuid}/deliveries — внутренний эндпоинт delivery-service (T+5 минут
     * после оплаты): CAS-перевод SUCCESS -> DELIVERING + событие в outbox (в событие попадает
     * ФИО курьера из тела запроса — для письма покупателю). 409 = заказ уже не SUCCESS
     * (успел возврат) — доставка обязана отмениться и освободить курьера.
     * Доступен только сервисному токену клиента delivery-service-client (проверка claim azp).
     */
    @Hidden
    @PostMapping("/orders/{orderUuid}/deliveries")
    public ResponseEntity<DeliveryStatusResponse> startDelivery(@Parameter(hidden = true) @PathVariable UUID orderUuid,
                                                                @Parameter(hidden = true) @RequestBody @Valid CourierRequest courierRequest,
                                                                @Parameter(hidden = true) JwtAuthenticationToken authentication) {
        requireServiceClient(authentication, "delivery-service-client");
        return new ResponseEntity<>(orderService.markOrderDelivering(orderUuid, courierRequest.courierName()), HttpStatus.OK);
    }

    /**
     * PATCH /orders/{orderUuid}/deliveries — внутренний эндпоинт delivery-service по завершении
     * доставки: CAS-перевод DELIVERING -> DELIVERED + событие в outbox. После DELIVERED возврат
     * невозможен. Доступен только delivery-service-client (claim azp).
     */
    @Hidden
    @PatchMapping("/orders/{orderUuid}/deliveries")
    public ResponseEntity<DeliveryStatusResponse> completeDelivery(@Parameter(hidden = true) @PathVariable UUID orderUuid,
                                                                   @Parameter(hidden = true) JwtAuthenticationToken authentication) {
        requireServiceClient(authentication, "delivery-service-client");
        return new ResponseEntity<>(orderService.markOrderDelivered(orderUuid), HttpStatus.OK);
    }

    /**
     * GET /users/{subId}/has-active-orders — внутренний эндпоинт auth-service перед удалением
     * аккаунта: есть ли заказы не в финальных статусах (активной считается и доставка).
     * Доступен только сервисному токену auth-service-client (claim azp).
     */
    @Hidden
    @GetMapping("/users/{subId}/has-active-orders")
    public ResponseEntity<Boolean> hasActiveOrders(@Parameter(hidden = true) @PathVariable String subId,
                                                   @Parameter(hidden = true) JwtAuthenticationToken authentication) {
        requireServiceClient(authentication, "auth-service-client");
        return new ResponseEntity<>(orderService.hasActiveOrders(subId), HttpStatus.OK);
    }

    /**
     * Service-to-service авторизация: claim azp (authorized party) в access-токене показывает,
     * какому клиенту Keycloak выдан токен. Разрешаем только ожидаемому сервисному клиенту —
     * обычный пользователь (azp=spring-microservices) эти эндпоинты вызвать не может.
     */
    private void requireServiceClient(JwtAuthenticationToken authentication, String expectedClientId) {
        String azp = authentication != null && authentication.getToken() != null
                ? authentication.getToken().getClaimAsString("azp") : null;
        if (!expectedClientId.equals(azp)) {
            log.warn("OrderController: доступ к внутреннему эндпоинту отклонён, azp={}", azp);
            throw new OrderAccessDeniedException("Ошибка: доступ разрешён только внутренним сервисам");
        }
    }
}
