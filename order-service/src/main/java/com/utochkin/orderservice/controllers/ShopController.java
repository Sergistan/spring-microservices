package com.utochkin.orderservice.controllers;


import com.utochkin.orderservice.web.OrderRequest;
import com.utochkin.orderservice.feign.StockChangeRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
/**
 * Feign-клиент к shop-service (напрямую через Eureka, мимо gateway).
 * checkOrder/getSumTotalPriceOrder — проверка товаров и суммы при создании заказа,
 * changeTotalQuantityProductsAfter{Create,Refunded}Order — списание/возврат остатков.
 */
//@FeignClient(name = "shop-service", url = "http://localhost:8072/shop/api/v1")
@FeignClient(name = "shop-service", path = "/shop/api/v1")
public interface ShopController {

    /** POST /checkOrder — хватает ли остатков на все позиции. */
    @PostMapping("/checkOrder")
    Boolean checkOrder(@RequestBody List<OrderRequest> orderRequests);

    /** POST /getSumTotalPriceOrder — сумма заказа (цена x количество). */
    @PostMapping("/getSumTotalPriceOrder")
    BigDecimal getSumTotalPriceOrder(@RequestBody List<OrderRequest> orderRequests);

    /** POST ...AfterCreateOrder — идемпотентное списание остатков. */
    @PostMapping("/changeTotalQuantityProductsAfterCreateOrder")
    void changeTotalQuantityProductsAfterCreateOrder(@RequestBody StockChangeRequest stockChangeRequest);

    /** POST ...AfterRefundedOrder — идемпотентный возврат остатков. */
    @PostMapping("/changeTotalQuantityProductsAfterRefundedOrder")
    void changeTotalQuantityProductsAfterRefundedOrder(@RequestBody StockChangeRequest stockChangeRequest);
}
