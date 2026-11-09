package com.utochkin.orderservice.controllers;

import com.utochkin.orderservice.request.OrderRequest;
import com.utochkin.orderservice.request.StockChangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
public class ShopControllerTest {

    @Mock
    private ShopController shopController;

    @Test
    @DisplayName("checkOrder возвращает true и вызывается с правильным списком")
    void checkOrder_ReturnsTrue() {
        OrderRequest req1 = new OrderRequest(UUID.randomUUID(), 2);
        OrderRequest req2 = new OrderRequest(UUID.randomUUID(), 5);
        List<OrderRequest> requests = List.of(req1, req2);

        given(shopController.checkOrder(requests)).willReturn(true);

        Boolean result = shopController.checkOrder(requests);

        assertThat(result).isTrue();

        ArgumentCaptor<List<OrderRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(shopController).checkOrder(captor.capture());
        assertThat(captor.getValue()).containsExactly(req1, req2);
    }

    @Test
    @DisplayName("getSumTotalPriceOrder возвращает сумму и вызывается с правильным списком")
    void getSumTotalPriceOrder_ReturnsSum() {
        OrderRequest req = new OrderRequest(UUID.randomUUID(), 3);
        List<OrderRequest> requests = List.of(req);
        given(shopController.getSumTotalPriceOrder(requests)).willReturn(BigDecimal.valueOf(123.45));

        BigDecimal sum = shopController.getSumTotalPriceOrder(requests);

        assertThat(sum).isEqualByComparingTo(BigDecimal.valueOf(123.45));

        ArgumentCaptor<List<OrderRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(shopController).getSumTotalPriceOrder(captor.capture());
        assertThat(captor.getValue()).containsExactly(req);
    }

    @Test
    @DisplayName("changeTotalQuantityProductsAfterCreateOrder вызывается с ключом заказа и списком позиций")
    void changeTotalQuantityProductsAfterCreateOrder_VerifyInvocation() {
        OrderRequest req = new OrderRequest(UUID.randomUUID(), 1);
        UUID orderUuid = UUID.randomUUID();
        StockChangeRequest request = new StockChangeRequest(orderUuid, List.of(req));

        shopController.changeTotalQuantityProductsAfterCreateOrder(request);

        ArgumentCaptor<StockChangeRequest> captor = ArgumentCaptor.forClass(StockChangeRequest.class);
        verify(shopController).changeTotalQuantityProductsAfterCreateOrder(captor.capture());
        assertThat(captor.getValue().getOrderUuid()).isEqualTo(orderUuid);
        assertThat(captor.getValue().getOrderRequests()).containsExactly(req);
    }

    @Test
    @DisplayName("changeTotalQuantityProductsAfterRefundedOrder вызывается с ключом заказа и списком позиций")
    void changeTotalQuantityProductsAfterRefundedOrder_VerifyInvocation() {
        OrderRequest req = new OrderRequest(UUID.randomUUID(), 4);
        UUID orderUuid = UUID.randomUUID();
        StockChangeRequest request = new StockChangeRequest(orderUuid, List.of(req));

        shopController.changeTotalQuantityProductsAfterRefundedOrder(request);

        ArgumentCaptor<StockChangeRequest> captor = ArgumentCaptor.forClass(StockChangeRequest.class);
        verify(shopController).changeTotalQuantityProductsAfterRefundedOrder(captor.capture());
        assertThat(captor.getValue().getOrderUuid()).isEqualTo(orderUuid);
        assertThat(captor.getValue().getOrderRequests()).containsExactly(req);
    }

}
