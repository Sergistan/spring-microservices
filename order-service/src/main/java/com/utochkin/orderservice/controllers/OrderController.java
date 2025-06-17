package com.utochkin.orderservice.controllers;

import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.models.ErrorResponse;
import com.utochkin.orderservice.models.User;
import com.utochkin.orderservice.request.CompositeRequest;
import com.utochkin.orderservice.request.PaymentRequest;
import com.utochkin.orderservice.request.PaymentResponse;
import com.utochkin.orderservice.services.OrderService;
import com.utochkin.orderservice.services.UserService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/order/api/v1")
@RequiredArgsConstructor
@Tag(name = "Order endpoints", description = "Order API")
public class OrderController {

    private final UserService userService;
    private final OrderService orderService;

    @PostMapping("/create")
    @Operation(summary = "Создание заказа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ оплачен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = OrderDto.class))),
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
            OrderDto orderDto = orderService.createOrder(user, compositeRequest.getOrderRequests(), compositeRequest.getAddressDto());
            return new ResponseEntity<>(orderDto, HttpStatus.CREATED);
        }
    }

    @PostMapping("/pay")
    @Operation(summary = "Оплата заказа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ успешно оплачен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "402", description = "Ошибка оплаты заказа", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> paymentOrder(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = PaymentRequest.class),
            encoding = @Encoding(contentType = "application/json")),
            description = "Ввод uuid заказа и номера карты для оплаты заказа", required = true) @Valid PaymentRequest paymentRequest) {
        return new ResponseEntity<>(orderService.paymentOrder(paymentRequest), HttpStatus.OK);
    }

    @PostMapping("/refunded")
    @Operation(summary = "Отмена заказа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ успешно отменен", content = @Content(schema = @Schema(implementation = String.class),
                    examples = @ExampleObject("Заказ успешно отменен"))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "402", description = "Ошибка оплаты заказа", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<?> refundedOrder(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = PaymentRequest.class),
            encoding = @Encoding(contentType = "application/json")),
            description = "Ввод uuid заказа и номера карты для отмены заказа", required = true) @Valid PaymentRequest paymentRequest) {
        orderService.refundedOrder(paymentRequest);
        return new ResponseEntity<>("Заказ успешно отменен", HttpStatus.OK);
    }

}
