package com.utochkin.orderservice.services;


import com.utochkin.orderservice.controllers.ShopController;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.dto.UserDto;
import com.utochkin.orderservice.exceptions.ServiceUnavailableException;
import com.utochkin.orderservice.mappers.OrderMapper;
import com.utochkin.orderservice.mappers.ProductInfoMapper;
import com.utochkin.orderservice.mappers.UserMapper;
import com.utochkin.orderservice.models.*;
import com.utochkin.orderservice.repositories.AddressRepository;
import com.utochkin.orderservice.repositories.OrderRepository;
import com.utochkin.orderservice.repositories.ProductInfoRepository;
import com.utochkin.orderservice.request.OrderRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
@Log4j2
public class OrderService {

    private final ShopController shopServiceClient;
    private final ProductInfoMapper productInfoMapper;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;
    private final ProductInfoRepository productInfoRepository;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    @CircuitBreaker(name = "circuitBreakerCheckOrder", fallbackMethod = "fallbackMethodCheckOrder")
    @Retry(name = "retryCheckOrder", fallbackMethod = "fallbackMethodCheckOrder")
    public Boolean checkOrder(List<OrderRequest> orderRequests) {
        return shopServiceClient.checkOrder(orderRequests);
    }

    public Boolean fallbackMethodCheckOrder(List<OrderRequest> orderRequests, Throwable throwable) {
        log.error("Fallback triggered for checkOrder due to: {}", throwable.getMessage());
        throw new ServiceUnavailableException("The service is temporarily unavailable, please try again later");
    }

    @Transactional
    @CircuitBreaker(name = "circuitBreakerCreateOrder", fallbackMethod = "fallbackMethodCreateOrder")
    @Retry(name = "retryCreateOrder", fallbackMethod = "fallbackMethodCreateOrder")
    public OrderDto createOrder(User user, List<OrderRequest> orderRequests, Address address) {
        Address savedAddress = addressRepository.save(address);

        List<ProductInfo> listEntity = productInfoMapper.toListEntity(orderRequests);
        productInfoRepository.saveAll(listEntity);

        Order order = new Order();
        order.setTotalAmount(shopServiceClient.getSumTotalPriceOrder(orderRequests));
        order.setOrderStatus(Status.WAITING_FOR_PAYMENT);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(null);
        order.setAddress(savedAddress);
        order.setUser(user);
        order.setProductInfos(listEntity);
        Order savedOrder = orderRepository.save(order);

        listEntity.forEach(productInfo -> productInfo.setOrder(savedOrder));

        shopServiceClient.changeTotalQuantityProductsAfterCreateOrder(orderRequests);

        UserDto userDto = userMapper.toDto(user);

        return orderMapper.toDto(savedOrder, userDto, orderRequests);
    }

    public OrderDto fallbackMethodCreateOrder(User user, List<OrderRequest> orderRequests, Address address, Throwable throwable) {
        log.error("Fallback triggered for createOrder due to: {}", throwable.getMessage());
        throw new ServiceUnavailableException("The service is temporarily unavailable, please try again later");
    }

}

