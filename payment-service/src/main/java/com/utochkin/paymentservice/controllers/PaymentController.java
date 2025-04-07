package com.utochkin.paymentservice.controllers;

import com.utochkin.paymentservice.models.PaymentResponse;
import com.utochkin.paymentservice.requests.AccountRequest;
import com.utochkin.paymentservice.requests.ErrorResponse;
import com.utochkin.paymentservice.services.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment/api/v1")
@RequiredArgsConstructor
@Tag(name = "Payment endpoints", description = "Payment API")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/pay")
    @Operation(summary = "Оплата заказа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ успешно оплачен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "402", description = "Ошибка оплаты заказа", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    PaymentResponse payOrder(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = AccountRequest.class),
            encoding = @Encoding(contentType = "application/json")),
            description = "Ввод uuid заказа и номера карты для оплаты заказа", required = true) @Valid AccountRequest accountRequest){
        return paymentService.paymentOrder(accountRequest);
    }

    @PostMapping("/refunded")
    @Operation(summary = "Отмена заказа")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Заказ успешно отменен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentResponse.class))),
            @ApiResponse(responseCode = "402", description = "Ошибка оплаты заказа", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    PaymentResponse refundedOrder(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = AccountRequest.class),
            encoding = @Encoding(contentType = "application/json")),
            description = "Ввод uuid заказа и номера карты для отмены заказа", required = true) @Valid AccountRequest accountRequest){
        return paymentService.refundedOrder(accountRequest);
    }

}
