package com.utochkin.shopservice.controllers;

import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.requests.ErrorResponse;
import com.utochkin.shopservice.requests.OrderRequest;
import com.utochkin.shopservice.services.ProductService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shop/api/v1")
@RequiredArgsConstructor
@Tag(name = "Shop endpoints", description = "Shop API")
public class ShopController {

    private final ProductService productService;

    @PostMapping("/checkOrder")
    @Hidden
    Boolean checkOrder(@RequestBody List<OrderRequest> orderRequests) {
        return productService.checkOrder(orderRequests);
    }

    @PostMapping("/getSumTotalPriceOrder")
    @Hidden
    Double getSumTotalPriceOrder(@RequestBody List<OrderRequest> orderRequests) {
        return productService.getSumTotalPriceOrder(orderRequests);
    }

    @PostMapping("/changeTotalQuantityProductsAfterCreateOrder")
    @Hidden
    void changeTotalQuantityProductsAfterCreateOrder(@RequestBody List<OrderRequest> orderRequests) {
        productService.changeTotalQuantityProductsAfterCreateOrder(orderRequests);
    }

    @PostMapping("/changeTotalQuantityProductsAfterRefundedOrder")
    @Hidden
    void changeTotalQuantityProductsAfterRefundedOrder(@RequestBody List<OrderRequest> orderRequests) {
        productService.changeTotalQuantityProductsAfterRefundedOrder(orderRequests);
    }

    @GetMapping("/getAllProducts")
    @Operation(summary = "Получение всех товаров в магазине")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар успешно получен", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ProductDto.class)))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<?> getAllProducts(@Parameter(description = "Номер страницы (начинается с 0)", example = "0")
                                     @RequestParam(value = "page", required = false) Integer page,

                                     @Parameter(description = "Размер страницы", example = "5")
                                     @RequestParam(value = "size", required = false) Integer size,

                                     @Parameter(description = "Сортировка (пример: name,asc или name,desc)", example = "name,asc")
                                     @RequestParam(value = "sort", required = false) String[] sort,

                                     @Parameter(hidden = true)
                                     @PageableDefault(page = 0, size = 5, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @GetMapping("/getProducts/{articleId}")
    @Operation(summary = "Получение товара по номеру артикула")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар успешно получен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<?> getProducts(@Parameter(
            description = "UUID артикула товара, который необходимо получить",
            example = "3873f81b-6d10-4860-97f4-0719eb88afaa") @PathVariable UUID articleId) {
        return ResponseEntity.ok(productService.getProduct(articleId));
    }

    @PostMapping("/addProduct")
    @Operation(summary = "Добавление товара в магазин")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Товар успешно добавлен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<?> addProduct(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = ProductDto.class),
            encoding = @Encoding(contentType = "application/json")),
            description = "Ввод товара для добавления в магазин", required = true) @Valid ProductDto productDto) {
        return new ResponseEntity<>(productService.addProduct(productDto), HttpStatus.CREATED);
    }

    @DeleteMapping("/deleteProduct/{articleId}")
    @Operation(summary = "Удаление товара из магазина")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар успешно удален", content = @Content(schema = @Schema(implementation = String.class),
                    examples = @ExampleObject("Товар с articleId = 3873f81b-6d10-4860-97f4-0719eb88afaa успешно удален"))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<?> deleteProduct(@Parameter(
            description = "UUID артикула товара, который будет удален",
            example = "3873f81b-6d10-4860-97f4-0719eb88afaa") @PathVariable UUID articleId) {
        productService.deleteProduct(articleId);
        return new ResponseEntity<>(String.format("Товар с articleId = %s успешно удален", articleId), HttpStatus.OK);
    }

    @PutMapping("/updateProduct/{articleId}")
    @Operation(summary = "Обновление товара в магазине")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар успешно обновлен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<?> updateProduct(@Parameter(
            description = "UUID артикула товара, который будет изменен",
            example = "3873f81b-6d10-4860-97f4-0719eb88afaa") @PathVariable UUID articleId,
                                    @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = ProductDto.class),
                                            encoding = @Encoding(contentType = "application/json")),
                                            description = "Ввод товара для изменения в магазине", required = true) @Valid ProductDto productDto) {
        return ResponseEntity.ok(productService.updateProduct(articleId, productDto));
    }

}
