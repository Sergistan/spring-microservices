package com.utochkin.orderservice.web;

import com.utochkin.orderservice.dto.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
/** Тело POST /orders: список позиций + адрес доставки. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompositeRequest {
    @NotEmpty(message = "Список товаров заказа не может быть пустым")
    private List<@Valid OrderRequest> orderRequests;
    private AddressDto addressDto;

}
