package com.utochkin.orderservice.services;


import com.utochkin.orderservice.controllers.PaymentController;
import com.utochkin.orderservice.controllers.ShopController;
import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.dto.UserDto;
import com.utochkin.orderservice.exceptions.*;
import com.utochkin.orderservice.mappers.AddressMapper;
import com.utochkin.orderservice.mappers.OrderMapper;
import com.utochkin.orderservice.mappers.ProductInfoMapper;
import com.utochkin.orderservice.mappers.UserMapper;
import com.utochkin.orderservice.models.*;
import com.utochkin.orderservice.repositories.OrderRepository;
import com.utochkin.orderservice.repositories.ProductInfoRepository;
import com.utochkin.orderservice.request.AccountRequest;
import com.utochkin.orderservice.request.OrderRequest;
import com.utochkin.orderservice.request.PaymentRequest;
import com.utochkin.orderservice.request.PaymentResponse;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Log4j2
public class OrderService {

    private final ShopController shopController;
    private final PaymentController paymentController;
    private final ProductInfoMapper productInfoMapper;
    private final OrderRepository orderRepository;
    private final ProductInfoRepository productInfoRepository;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final AddressMapper addressMapper;
    private final KafkaSenderService kafkaSenderService;

    @Transactional(readOnly = true)
    @CircuitBreaker(name = "circuitBreakerCheckOrder", fallbackMethod = "fallbackMethodCheckOrder")
    @Retry(name = "retryCheckOrder", fallbackMethod = "fallbackMethodCheckOrder")
    public Boolean checkOrder(List<OrderRequest> orderRequests) {
        log.info("OrderService: проверка наличия товаров: {}", orderRequests);
        return shopController.checkOrder(orderRequests);
    }

    public Boolean fallbackMethodCheckOrder(List<OrderRequest> orderRequests, Throwable throwable) {
        log.error("Fallback для checkOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }

    @Transactional
    @CircuitBreaker(name = "circuitBreakerCreateOrder", fallbackMethod = "fallbackMethodCreateOrder")
    @Retry(name = "retryCreateOrder", fallbackMethod = "fallbackMethodCreateOrder")
    public OrderDto createOrder(User user, List<OrderRequest> orderRequests, AddressDto addressDto) {
        log.info("OrderService: начало создания заказа для user={} requests={}", user.getUsername(), orderRequests);

        Address address = addressMapper.toEntity(addressDto);

        List<ProductInfo> listEntity = productInfoMapper.toListEntity(orderRequests);
        productInfoRepository.saveAll(listEntity);

        Order order = new Order();
        order.setOrderUuid(UUID.randomUUID());
        order.setTotalAmount(shopController.getSumTotalPriceOrder(orderRequests));
        order.setOrderStatus(Status.WAITING_FOR_PAYMENT);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(null);
        order.setAddress(address);
        order.setUser(user);
        order.setPaymentId(null);
        order.setProductInfos(listEntity);
        Order savedOrder = orderRepository.save(order);

        listEntity.forEach(productInfo -> productInfo.setOrder(savedOrder));

        shopController.changeTotalQuantityProductsAfterCreateOrder(orderRequests);

        UserDto userDto = userMapper.toDto(user);

        log.info("OrderService: заказ {} создан успешно", savedOrder.getOrderUuid());

        return orderMapper.toDto(savedOrder, userDto, addressDto, orderRequests);
    }

    public OrderDto fallbackMethodCreateOrder(User user, List<OrderRequest> orderRequests, AddressDto addressDto, Throwable throwable) {
        log.error("Fallback для createOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }

    @Transactional(noRollbackFor = FailedPayOrderException.class)
    @CircuitBreaker(name = "circuitBreakerPayOrder", fallbackMethod = "fallbackMethodPayOrder")
    @Retry(name = "retryPayOrder", fallbackMethod = "fallbackMethodPayOrder")
    public PaymentResponse paymentOrder(PaymentRequest paymentRequest) {
        log.info("OrderService: оплата заказа {}", paymentRequest.getOrderUuid());

        Optional<Order> orderById = orderRepository.findByOrderUuid(paymentRequest.getOrderUuid());
        if (orderById.isPresent()) {
            Order order = orderById.get();
            AddressDto addressDto = addressMapper.toDto(order.getAddress());
            switch (order.getOrderStatus()) {
                case SUCCESS -> throw new FailedOrderStatusException("Заказ уже оплачен!");
                case REFUNDED -> throw new FailedOrderStatusException("Заказ отменен, необходимо создать новый заказ!");
            }

            Double totalAmountById = orderRepository.findTotalAmountByOrderUuid(paymentRequest.getOrderUuid());
            PaymentResponse paymentResponse = paymentController.paymentOrder(new AccountRequest(totalAmountById, paymentRequest.getCardNumber()));
            switch (paymentResponse.getStatus()) {
                case Status.SUCCESS -> {
                    order.setOrderStatus(Status.SUCCESS);
                    order.setUpdatedAt(LocalDateTime.now());
                    order.setPaymentId(paymentResponse.getPaymentId());
                    Order saveOrder = orderRepository.save(order);

                    List<OrderRequest> orderRequests = order.getProductInfos()
                            .stream()
                            .map(productInfo -> new OrderRequest(
                                    productInfo.getArticleId(),
                                    productInfo.getQuantity()
                            ))
                            .toList();

                    OrderDtoForKafka dtoForKafka = orderMapper.toDtoForKafka(saveOrder, userMapper.toDto(saveOrder.getUser()), addressDto, orderRequests);

                    kafkaSenderService.send(dtoForKafka);
                }
                case Status.FAILED -> {
                    order.setOrderStatus(Status.FAILED);
                    order.setUpdatedAt(LocalDateTime.now());
                    order.setPaymentId(paymentResponse.getPaymentId());
                    Order saveOrder = orderRepository.save(order);

                    List<OrderRequest> orderRequests = order.getProductInfos()
                            .stream()
                            .map(productInfo -> new OrderRequest(
                                    productInfo.getArticleId(),
                                    productInfo.getQuantity()
                            ))
                            .toList();

                    OrderDtoForKafka dtoForKafka = orderMapper.toDtoForKafka(saveOrder, userMapper.toDto(saveOrder.getUser()), addressDto, orderRequests);

                    kafkaSenderService.send(dtoForKafka);

                    throw new FailedPayOrderException();
                }
            }
            log.info("OrderService: статус оплаты {} для заказа {}", paymentResponse.getStatus(), paymentRequest.getOrderUuid());

            return paymentResponse;
        } else {
            throw new OrderNotFoundException();
        }
    }

    public PaymentResponse fallbackMethodPayOrder(PaymentRequest paymentRequest, Throwable throwable) {
        extractedFullbackMethod(paymentRequest, throwable);
        log.error("Fallback для paymentOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }


    @Transactional
    @CircuitBreaker(name = "circuitBreakerRefundedOrder", fallbackMethod = "fallbackMethodRefundedOrder")
    @Retry(name = "retryRefundedOrder", fallbackMethod = "fallbackMethodRefundedOrder")
    public void refundedOrder(PaymentRequest paymentRequest) {
        log.info("OrderService: возврат заказа {}", paymentRequest.getOrderUuid());

        Optional<Order> orderById = orderRepository.findByOrderUuid(paymentRequest.getOrderUuid());
        if (orderById.isPresent()) {
            Order order = orderById.get();
            AddressDto addressDto = addressMapper.toDto(order.getAddress());
            switch (order.getOrderStatus()) {
                case WAITING_FOR_PAYMENT, FAILED, REFUNDED ->
                        throw new FailedOrderStatusException("Заказ нельзя отменить, т.к. он не был оплачен!");
            }

            Double totalAmountById = orderRepository.findTotalAmountByOrderUuid(paymentRequest.getOrderUuid());
            PaymentResponse refundedOrder = paymentController.refundedOrder(new AccountRequest(totalAmountById, paymentRequest.getCardNumber()));
            if (refundedOrder.getStatus().equals(Status.REFUNDED)) {
                order.setOrderStatus(Status.REFUNDED);
                order.setUpdatedAt(LocalDateTime.now());
                order.setPaymentId(refundedOrder.getPaymentId());

                List<ProductInfo> productInfos = order.getProductInfos();

                List<OrderRequest> orderRequests = productInfos.stream()
                        .map(productInfo -> {
                            OrderRequest orderRequest = new OrderRequest();
                            orderRequest.setArticleId(productInfo.getArticleId());
                            orderRequest.setQuantity(productInfo.getQuantity());
                            return orderRequest;
                        })
                        .toList();

                shopController.changeTotalQuantityProductsAfterRefundedOrder(orderRequests);

                order.setProductInfos(Collections.emptyList());

                orderRepository.save(order);

                List<Long> productIds = productInfos.stream().map(ProductInfo::getId).toList();

                productInfoRepository.deleteAllById(productIds);

                Order saveOrder = orderRepository.save(order);

                OrderDtoForKafka dtoForKafka = orderMapper.toDtoForKafka(saveOrder, userMapper.toDto(saveOrder.getUser()), addressDto, orderRequests);

                kafkaSenderService.send(dtoForKafka);

                log.info("OrderService: возврат выполнен {}", paymentRequest.getOrderUuid());
            }
        } else {
            throw new OrderNotFoundException();
        }
    }

    public void fallbackMethodRefundedOrder(PaymentRequest paymentRequest, Throwable throwable) {
        extractedFullbackMethod(paymentRequest, throwable);
        log.error("Fallback для refundedOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }

    private static void extractedFullbackMethod(PaymentRequest paymentRequest, Throwable throwable) {
        if (throwable instanceof FeignException fe) {
            if (fe.status() == HttpStatus.NOT_FOUND.value()) {
                log.warn("Карта {} не найдена в платёжном сервисе (404)", paymentRequest.getCardNumber());
                throw new CardNumberNotFoundException();
            }
            if (fe.status() == HttpStatus.PAYMENT_REQUIRED.value()) {
                log.warn("Недостаточно средств на карте {} (402)", paymentRequest.getCardNumber());
                throw new FailedPayOrderException();
            }
        }
        if (throwable instanceof FailedOrderStatusException ||
                throwable instanceof CardNumberNotFoundException ||
                throwable instanceof FailedPayOrderException ||
                throwable instanceof OrderNotFoundException) {
            throw throwable instanceof RuntimeException
                    ? (RuntimeException) throwable
                    : new RuntimeException(throwable);
        }
    }
}

