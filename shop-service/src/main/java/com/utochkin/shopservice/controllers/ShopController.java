package com.utochkin.shopservice.controllers;

import com.utochkin.shopservice.dto.ProductDto;
import com.utochkin.shopservice.request.OrderRequest;
import com.utochkin.shopservice.services.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shop/api/v1")
@RequiredArgsConstructor
public class ShopController {

    private final ProductService productService;

    @PostMapping("/checkOrder")
    Boolean checkOrder(@RequestBody List<OrderRequest> orderRequests){
       return productService.checkOrder(orderRequests);
    }

    @PostMapping("/getSumTotalPriceOrder")
    Double getSumTotalPriceOrder(@RequestBody List<OrderRequest> orderRequests){
            return productService.getSumTotalPriceOrder(orderRequests);
    }

    @PostMapping("/changeTotalQuantityProductsAfterCreateOrder")
    void changeTotalQuantityProductsAfterCreateOrder(@RequestBody List<OrderRequest> orderRequests){
        productService.changeTotalQuantityProductsAfterCreateOrder(orderRequests);
    }

    @GetMapping("/getAllProducts")
    ResponseEntity<?> getAllProducts(){
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/getProducts/{articleId}")
    ResponseEntity<?> getProducts(@PathVariable UUID articleId){
        return ResponseEntity.ok(productService.getProduct(articleId));
    }

    @PostMapping("/addProduct")
    ResponseEntity<?> addProduct(@RequestBody ProductDto productDto){
        return new ResponseEntity<>(productService.addProduct(productDto), HttpStatus.CREATED);
    }

    @DeleteMapping("/deleteProduct/{articleId}")
    ResponseEntity<?> deleteProduct(@PathVariable UUID articleId){
        productService.deleteProduct(articleId);
        return new ResponseEntity<>(String.format("Product with articleId = %s deleted", articleId), HttpStatus.OK);
    }

    @PutMapping("/updateProduct/{articleId}")
    ResponseEntity<?> updateProduct(@PathVariable UUID articleId, @RequestBody ProductDto productDto){
        return ResponseEntity.ok(productService.updateProduct(articleId, productDto));
    }


}
