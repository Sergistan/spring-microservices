package com.utochkin.shopservice.controllers;

import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.web.ProductDtoRequest;
import com.utochkin.shopservice.dto.ErrorResponse;
import com.utochkin.shopservice.feign.OrderRequest;
import com.utochkin.shopservice.feign.StockChangeRequest;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * REST API каталога shop-service. Два рода потребителей:
 *  - человек/фронтенд через getaway-server: GET /products, CRUD товара (роль ADMIN);
 *  - order-service напрямую через Eureka (Feign): POST /stock-verifications,
 *    /price-calculations, /stock-deductions, /stock-restorations — проверка и
 *    атомарное идемпотентное списание/возврат остатков.
 */
@RestController
@RequestMapping("/shop/api/v1")
@RequiredArgsConstructor
@Tag(name = "Shop endpoints", description = "Shop API")
public class ShopController {

    private final ProductService productService;

    /** Внутренний эндпоинт для order-service (Feign, @Hidden из swagger): проверка наличия товаров. */
    @PostMapping("/stock-verifications")
    @Hidden
    Boolean checkOrder(@RequestBody List<OrderRequest> orderRequests) {
        return productService.checkOrder(orderRequests);
    }

    /** Внутренний (Feign, @Hidden): расчёт суммы заказа по позициям. */
    @PostMapping("/price-calculations")
    @Hidden
    BigDecimal getSumTotalPriceOrder(@RequestBody List<OrderRequest> orderRequests) {
        return productService.getSumTotalPriceOrder(orderRequests);
    }

    /** Внутренний (Feign, @Hidden): идемпотентное списание остатков после создания заказа. */
    @PostMapping("/stock-deductions")
    @Hidden
    void changeTotalQuantityProductsAfterCreateOrder(@RequestBody @Valid StockChangeRequest stockChangeRequest) {
        productService.changeTotalQuantityProductsAfterCreateOrder(stockChangeRequest.getOrderUuid(), stockChangeRequest.getOrderRequests());
    }

    /** Внутренний (Feign, @Hidden): идемпотентный возврат остатков после отмены заказа. */
    @PostMapping("/stock-restorations")
    @Hidden
    void changeTotalQuantityProductsAfterRefundedOrder(@RequestBody @Valid StockChangeRequest stockChangeRequest) {
        productService.changeTotalQuantityProductsAfterRefundedOrder(stockChangeRequest.getOrderUuid(), stockChangeRequest.getOrderRequests());
    }

    /** GET /products: каталог постранично (page/size/sort -> Pageable; ответ из Redis-кэша). */
    @GetMapping("/products")
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

    /** GET /products/{articleId}: карточка товара (кэш; 404 если нет). */
    @GetMapping("/products/{articleId}")
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

    /** POST /products: создание товара (только ADMIN — см. Config). */
    @PostMapping("/products")
    @Operation(summary = "Добавление товара в магазин")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Товар успешно добавлен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "400", description = "Плохой запрос", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Не найдено", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Ошибка сервера", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<?> addProduct(@RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = ProductDtoRequest.class),
            encoding = @Encoding(contentType = "application/json")),
            description = "Ввод товара для добавления в магазин", required = true) @Valid ProductDtoRequest productDtoRequest) {
        return new ResponseEntity<>(productService.addProduct(productDtoRequest), HttpStatus.CREATED);
    }

    /** DELETE /products/{articleId}: удаление товара (только ADMIN). */
    @DeleteMapping("/products/{articleId}")
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

    /** PUT /products/{articleId}: обновление карточки товара (только ADMIN). */
    @PutMapping("/products/{articleId}")
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
                                    @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = ProductDtoRequest.class),
                                            encoding = @Encoding(contentType = "application/json")),
                                            description = "Ввод товара для изменения в магазине", required = true) @Valid ProductDtoRequest productDtoRequest) {
        return ResponseEntity.ok(productService.updateProduct(articleId, productDtoRequest));
    }

}
