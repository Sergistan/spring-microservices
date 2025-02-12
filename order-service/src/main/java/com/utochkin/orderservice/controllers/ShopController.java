package com.utochkin.orderservice.controllers;


import com.utochkin.orderservice.request.OrderRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
@FeignClient(name = "shop-service", url = "http://localhost:8072/shop/api/v1")
public interface ShopController {

    // Проверяет в shop-service, возможно ли создать заказ с таким количеством OrderRequest
    @PostMapping("/checkOrder")
    Boolean checkOrder(@RequestBody List<OrderRequest> orderRequests);

    // Отправляет из shop-service, общую сумму получившегося заказа по OrderRequest
    @PostMapping("/getSumTotalPriceOrder")
    Double getSumTotalPriceOrder(@RequestBody List<OrderRequest> orderRequests);

    @PostMapping("/changeTotalQuantityProductsAfterCreateOrder")
    void changeTotalQuantityProductsAfterCreateOrder(@RequestBody List<OrderRequest> orderRequests);

}
