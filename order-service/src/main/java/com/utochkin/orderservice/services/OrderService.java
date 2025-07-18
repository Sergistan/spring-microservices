package com.utochkin.orderservice.services;


import com.utochkin.orderservice.controllers.PaymentController;
import com.utochkin.orderservice.controllers.ShopController;
import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.exceptions.*;
import com.utochkin.orderservice.mappers.AddressMapper;
import com.utochkin.orderservice.mappers.OrderMapper;
import com.utochkin.orderservice.mappers.ProductInfoMapper;
import com.utochkin.orderservice.mappers.UserMapper;
import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.models.ProductInfo;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.models.User;
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
import java.util.UUID;

import static com.utochkin.orderservice.models.Status.REFUNDED;


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

    private void processPaymentResult(Order order, PaymentResponse paymentResponse) {
        order.setOrderStatus(paymentResponse.getStatus());
        order.setUpdatedAt(LocalDateTime.now());
        order.setPaymentId(paymentResponse.getPaymentId());
        Order savedOrder = orderRepository.save(order);

        List<OrderRequest> orderRequests = savedOrder.getProductInfos().stream()
                .map(productInfo -> new OrderRequest(productInfo.getArticleId(), productInfo.getQuantity()))
                .toList();

        OrderDtoForKafka dto = orderMapper.toDtoForKafka(
                savedOrder,
                userMapper.toDto(savedOrder.getUser()),
                addressMapper.toDto(savedOrder.getAddress()),
                orderRequests
        );
        kafkaSenderService.send(dto);

        log.info("OrderService: отправлено событие {} для заказа {}", paymentResponse.getStatus(), savedOrder.getOrderUuid());
    }

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

        List<ProductInfo> listEntity = productInfoMapper.toListEntity(orderRequests);
        productInfoRepository.saveAll(listEntity);

        Order order = Order.builder()
                .orderUuid(UUID.randomUUID())
                .totalAmount(shopController.getSumTotalPriceOrder(orderRequests))
                .orderStatus(Status.WAITING_FOR_PAYMENT)
                .createdAt(LocalDateTime.now())
                .updatedAt(null)
                .address(addressMapper.toEntity(addressDto))
                .user(user)
                .paymentId(null)
                .productInfos(listEntity)
                .build();

        Order savedOrder = orderRepository.save(order);

        listEntity.forEach(productInfo -> productInfo.setOrder(savedOrder));

        shopController.changeTotalQuantityProductsAfterCreateOrder(orderRequests);

        log.info("OrderService: заказ {} создан успешно", savedOrder.getOrderUuid());

        return orderMapper.toDto(savedOrder, userMapper.toDto(user), addressDto, orderRequests);
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

        Order order = orderRepository.findByOrderUuid(paymentRequest.getOrderUuid()).orElseThrow(OrderNotFoundException::new);

        switch (order.getOrderStatus()) {
            case SUCCESS -> throw new FailedOrderStatusException("Заказ уже оплачен!");
            case REFUNDED -> throw new FailedOrderStatusException("Заказ отменен, необходимо создать новый заказ!");
        }

        Double totalAmountById = orderRepository.findTotalAmountByOrderUuid(paymentRequest.getOrderUuid());
        PaymentResponse paymentResponse = paymentController.paymentOrder(new AccountRequest(totalAmountById, paymentRequest.getCardNumber()));

        processPaymentResult(order, paymentResponse);

        if (paymentResponse.getStatus() == Status.FAILED) {
            throw new FailedPayOrderException();
        }

        return paymentResponse;
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

        Order order = orderRepository.findByOrderUuid(paymentRequest.getOrderUuid()).orElseThrow(OrderNotFoundException::new);

        switch (order.getOrderStatus()) {
            case WAITING_FOR_PAYMENT, FAILED, REFUNDED ->
                    throw new FailedOrderStatusException("Заказ нельзя отменить, т.к. он не был оплачен!");
        }

        Double totalAmountById = orderRepository.findTotalAmountByOrderUuid(paymentRequest.getOrderUuid());
        PaymentResponse refundedOrder = paymentController.refundedOrder(new AccountRequest(totalAmountById, paymentRequest.getCardNumber()));

        if (refundedOrder.getStatus() == REFUNDED) {

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

            List<Long> productIds = productInfos.stream().map(ProductInfo::getId).toList();

            productInfoRepository.deleteAllById(productIds);

            Order saveOrder = orderRepository.save(order);

            OrderDtoForKafka dtoForKafka = orderMapper.toDtoForKafka(saveOrder, userMapper.toDto(saveOrder.getUser()), addressMapper.toDto(order.getAddress()), orderRequests);

            kafkaSenderService.send(dtoForKafka);

            log.info("OrderService: возврат выполнен {}", paymentRequest.getOrderUuid());
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
            throw (RuntimeException) throwable;
        }
    }
}

