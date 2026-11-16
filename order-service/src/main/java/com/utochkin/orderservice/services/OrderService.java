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
import com.utochkin.orderservice.request.OrderPaymentsResponse;
import com.utochkin.orderservice.request.OrderRequest;
import com.utochkin.orderservice.request.PaymentRequest;
import com.utochkin.orderservice.request.PaymentResponse;
import com.utochkin.orderservice.request.StockChangeRequest;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.utochkin.orderservice.models.Status.REFUNDED;


@Service
@RequiredArgsConstructor
@Log4j2
public class OrderService {

    private static final List<Status> PAYABLE_STATUSES = List.of(Status.WAITING_FOR_PAYMENT, Status.FAILED, Status.PROCESSING);
    private static final List<Status> REFUNDABLE_STATUSES = List.of(Status.SUCCESS);

    private final ShopController shopController;
    private final PaymentController paymentController;
    private final ProductInfoMapper productInfoMapper;
    private final OrderRepository orderRepository;
    private final ProductInfoRepository productInfoRepository;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;
    private final AddressMapper addressMapper;
    private final OutboxService outboxService;
    private final TransactionTemplate transactionTemplate;

    private void finalizePaymentResult(UUID orderUuid, PaymentResponse paymentResponse) {
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findByOrderUuid(orderUuid).orElseThrow(OrderNotFoundException::new);
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
            outboxService.saveToOutbox(dto);
        });
        log.info("OrderService: заказ {} переведён в {}, событие зафиксировано в outbox", orderUuid, paymentResponse.getStatus());
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

    @CircuitBreaker(name = "circuitBreakerCreateOrder", fallbackMethod = "fallbackMethodCreateOrder")
    @Retry(name = "retryCreateOrder", fallbackMethod = "fallbackMethodCreateOrder")
    public OrderDto createOrder(User user, UUID orderUuid, List<OrderRequest> orderRequests, AddressDto addressDto) {
        log.info("OrderService: начало создания заказа {} для user={} requests={}", orderUuid, user.getUsername(), orderRequests);

        BigDecimal totalAmount = shopController.getSumTotalPriceOrder(orderRequests);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                Order order = Order.builder()
                        .orderUuid(orderUuid)
                        .totalAmount(totalAmount)
                        .orderStatus(Status.STOCK_PENDING)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .address(addressMapper.toEntity(addressDto))
                        .user(user)
                        .paymentId(null)
                        .build();

                Order savedOrder = orderRepository.saveAndFlush(order);

                List<ProductInfo> listEntity = productInfoMapper.toListEntity(orderRequests);
                listEntity.forEach(productInfo -> productInfo.setOrder(savedOrder));
                productInfoRepository.saveAll(listEntity);
            });
        } catch (DataIntegrityViolationException e) {
            log.info("OrderService: заказ {} уже создан предыдущей попыткой — идемпотентный повтор", orderUuid);
        }

        finalizeStock(orderUuid, orderRequests);

        Order result = orderRepository.findByOrderUuid(orderUuid).orElseThrow(OrderNotFoundException::new);
        log.info("OrderService: заказ {} создан, статус {}", orderUuid, result.getOrderStatus());
        return orderMapper.toDto(result, userMapper.toDto(user), addressDto, orderRequests);
    }

    public void finalizeStock(UUID orderUuid, List<OrderRequest> orderRequests) {
        try {
            shopController.changeTotalQuantityProductsAfterCreateOrder(new StockChangeRequest(orderUuid, orderRequests));
        } catch (FeignException fe) {
            if (fe.status() == HttpStatus.CONFLICT.value() || fe.status() == HttpStatus.NOT_FOUND.value()) {
                transactionTemplate.executeWithoutResult(status ->
                        orderRepository.transitionStatus(orderUuid, List.of(Status.STOCK_PENDING), Status.FAILED));
                log.warn("OrderService: товар недоступен для заказа {} — заказ переведён в FAILED", orderUuid);
                throw new ProductUnavailableException();
            }
            throw fe;
        }
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.transitionStatus(orderUuid, List.of(Status.STOCK_PENDING), Status.WAITING_FOR_PAYMENT));
        log.info("OrderService: списание подтверждено, заказ {} переведён в WAITING_FOR_PAYMENT", orderUuid);
    }

    public void fallbackMethodCreateOrder(User user, UUID orderUuid, List<OrderRequest> orderRequests, AddressDto addressDto, Throwable throwable) {
        if (throwable instanceof FeignException fe) {
            if (fe.status() == HttpStatus.CONFLICT.value() || fe.status() == HttpStatus.NOT_FOUND.value()) {
                log.warn("OrderService: товар недоступен или недостаточен при создании заказа {} ({})", orderUuid, fe.status());
                throw new ProductUnavailableException();
            }
        }
        if (throwable instanceof ProductUnavailableException) {
            throw (RuntimeException) throwable;
        }
        log.error("Fallback для createOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }

    @CircuitBreaker(name = "circuitBreakerPayOrder", fallbackMethod = "fallbackMethodPayOrder")
    @Retry(name = "retryPayOrder", fallbackMethod = "fallbackMethodPayOrder")
    public PaymentResponse paymentOrder(PaymentRequest paymentRequest, String subId) {
        log.info("OrderService: оплата заказа {}", paymentRequest.getOrderUuid());

        Order order = orderRepository.findByOrderUuid(paymentRequest.getOrderUuid()).orElseThrow(OrderNotFoundException::new);

        if (!order.getUser().getSubId().equals(subId)) {
            log.warn("OrderService: попытка оплатить заказ {} пользователем subId={}", paymentRequest.getOrderUuid(), subId);
            throw new OrderAccessDeniedException();
        }

        switch (order.getOrderStatus()) {
            case SUCCESS -> throw new FailedOrderStatusException("Заказ уже оплачен!");
            case REFUNDED -> throw new FailedOrderStatusException("Заказ отменен, необходимо создать новый заказ!");
            case STOCK_PENDING -> throw new FailedOrderStatusException("Заказ ещё подтверждается, попробуйте позже");
        }

        Integer acquired = transactionTemplate.execute(status ->
                orderRepository.transitionStatus(paymentRequest.getOrderUuid(), PAYABLE_STATUSES, Status.PROCESSING));
        if (acquired == null || acquired == 0) {
            throw new FailedOrderStatusException("Заказ уже оплачен!");
        }

        BigDecimal totalAmountById = orderRepository.findTotalAmountByOrderUuid(paymentRequest.getOrderUuid());
        PaymentResponse paymentResponse = paymentController.paymentOrder(new AccountRequest(
                paymentRequest.getOrderUuid(), totalAmountById, paymentRequest.getCardNumber()));

        finalizePaymentResult(paymentRequest.getOrderUuid(), paymentResponse);

        if (paymentResponse.getStatus() == Status.FAILED) {
            throw new FailedPayOrderException();
        }

        return paymentResponse;
    }

    public PaymentResponse fallbackMethodPayOrder(PaymentRequest paymentRequest, String subId, Throwable throwable) {
        extractedFullbackMethod(paymentRequest, throwable);
        log.error("Fallback для paymentOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }


    @CircuitBreaker(name = "circuitBreakerRefundedOrder", fallbackMethod = "fallbackMethodRefundedOrder")
    @Retry(name = "retryRefundedOrder", fallbackMethod = "fallbackMethodRefundedOrder")
    public void refundedOrder(PaymentRequest paymentRequest, String subId) {
        log.info("OrderService: возврат заказа {}", paymentRequest.getOrderUuid());

        Order order = orderRepository.findByOrderUuid(paymentRequest.getOrderUuid()).orElseThrow(OrderNotFoundException::new);

        if (!order.getUser().getSubId().equals(subId)) {
            log.warn("OrderService: попытка вернуть заказ {} пользователем subId={}", paymentRequest.getOrderUuid(), subId);
            throw new OrderAccessDeniedException();
        }

        switch (order.getOrderStatus()) {
            case STOCK_PENDING, WAITING_FOR_PAYMENT, PROCESSING, FAILED, REFUNDED ->
                    throw new FailedOrderStatusException("Заказ нельзя отменить, т.к. он не был оплачен!");
        }

        Integer acquired = transactionTemplate.execute(status ->
                orderRepository.transitionStatus(paymentRequest.getOrderUuid(), REFUNDABLE_STATUSES, Status.PROCESSING));
        if (acquired == null || acquired == 0) {
            throw new FailedOrderStatusException("Заказ нельзя отменить, т.к. он не был оплачен!");
        }

        BigDecimal totalAmountById = orderRepository.findTotalAmountByOrderUuid(paymentRequest.getOrderUuid());
        PaymentResponse refundedOrder = paymentController.refundedOrder(new AccountRequest(
                paymentRequest.getOrderUuid(), totalAmountById, paymentRequest.getCardNumber()));

        if (refundedOrder.getStatus() == REFUNDED) {

            transactionTemplate.executeWithoutResult(status -> {
                Order fresh = orderRepository.findByOrderUuid(paymentRequest.getOrderUuid()).orElseThrow(OrderNotFoundException::new);

                fresh.setOrderStatus(Status.REFUNDED);
                fresh.setUpdatedAt(LocalDateTime.now());
                fresh.setPaymentId(refundedOrder.getPaymentId());

                List<ProductInfo> productInfos = fresh.getProductInfos();

                List<OrderRequest> orderRequests = productInfos.stream()
                        .map(productInfo -> new OrderRequest(productInfo.getArticleId(), productInfo.getQuantity()))
                        .toList();

                shopController.changeTotalQuantityProductsAfterRefundedOrder(
                        new StockChangeRequest(paymentRequest.getOrderUuid(), orderRequests));

                fresh.setProductInfos(Collections.emptyList());

                List<Long> productIds = productInfos.stream().map(ProductInfo::getId).toList();
                productInfoRepository.deleteAllById(productIds);

                Order savedOrder = orderRepository.save(fresh);

                OrderDtoForKafka dtoForKafka = orderMapper.toDtoForKafka(savedOrder, userMapper.toDto(savedOrder.getUser()), addressMapper.toDto(fresh.getAddress()), orderRequests);
                outboxService.saveToOutbox(dtoForKafka);
            });

            log.info("OrderService: возврат выполнен {}, событие зафиксировано в outbox", paymentRequest.getOrderUuid());
        }
    }

    public void fallbackMethodRefundedOrder(PaymentRequest paymentRequest, String subId, Throwable throwable) {
        extractedFullbackMethod(paymentRequest, throwable);
        log.error("Fallback для refundedOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }

    private static void extractedFullbackMethod(PaymentRequest paymentRequest, Throwable throwable) {
        if (throwable instanceof FeignException fe) {
            if (fe.status() == HttpStatus.NOT_FOUND.value()) {
                log.warn("Карта {} не найдена в платёжном сервисе (404)", maskCard(paymentRequest.getCardNumber()));
                throw new CardNumberNotFoundException();
            }
            if (fe.status() == HttpStatus.PAYMENT_REQUIRED.value()) {
                log.warn("Недостаточно средств на карте {} (402)", maskCard(paymentRequest.getCardNumber()));
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

    private static String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    public void reconcile(Order order) {
        if (order.getOrderStatus() == Status.STOCK_PENDING) {
            log.info("OrderService: reconciliation: заказ {} в STOCK_PENDING — повторная попытка списания", order.getOrderUuid());
            try {
                finalizeStock(order.getOrderUuid(), toOrderRequests(order.getProductInfos()));
            } catch (ProductUnavailableException e) {
                log.warn("OrderService: reconciliation: товар для заказа {} недоступен — заказ переведён в FAILED", order.getOrderUuid());
            }
            return;
        }
        OrderPaymentsResponse payments = paymentController.getOrderPayments(order.getOrderUuid());
        reconcileStuckOrder(order, payments);
    }

    private List<OrderRequest> toOrderRequests(List<ProductInfo> productInfos) {
        return productInfos.stream()
                .map(productInfo -> new OrderRequest(productInfo.getArticleId(), productInfo.getQuantity()))
                .toList();
    }

    public void reconcileStuckOrder(Order order, OrderPaymentsResponse payments) {
        if (payments.refunded()) {
            finalizeReconciledRefund(order, payments);
        } else if (payments.paid()) {
            finalizeReconciledSuccess(order, payments);
        } else {
            Integer released = transactionTemplate.execute(status ->
                    orderRepository.transitionStatus(order.getOrderUuid(), List.of(Status.PROCESSING), Status.WAITING_FOR_PAYMENT));
            if (released != null && released > 0) {
                log.info("OrderService: reconciliation: заказ {} без платежа — возвращён в WAITING_FOR_PAYMENT", order.getOrderUuid());
            } else {
                log.info("OrderService: reconciliation: заказ {} уже не в PROCESSING — пропущен", order.getOrderUuid());
            }
        }
    }

    private void finalizeReconciledSuccess(Order order, OrderPaymentsResponse payments) {
        transactionTemplate.executeWithoutResult(status -> {
            Order fresh = orderRepository.findByOrderUuid(order.getOrderUuid()).orElseThrow(OrderNotFoundException::new);
            if (fresh.getOrderStatus() != Status.PROCESSING) {
                status.setRollbackOnly();
                return;
            }
            fresh.setOrderStatus(Status.SUCCESS);
            fresh.setUpdatedAt(LocalDateTime.now());
            fresh.setPaymentId(payments.paymentId());
            Order savedOrder = orderRepository.save(fresh);
            outboxService.saveToOutbox(toKafkaDto(savedOrder));
            log.info("OrderService: reconciliation: заказ {} финализирован как SUCCESS, событие зафиксировано в outbox", order.getOrderUuid());
        });
    }

    private void finalizeReconciledRefund(Order order, OrderPaymentsResponse payments) {
        transactionTemplate.executeWithoutResult(status -> {
            Order fresh = orderRepository.findByOrderUuid(order.getOrderUuid()).orElseThrow(OrderNotFoundException::new);
            if (fresh.getOrderStatus() != Status.PROCESSING) {
                status.setRollbackOnly();
                return;
            }

            List<ProductInfo> productInfos = fresh.getProductInfos();
            List<OrderRequest> orderRequests = toOrderRequests(productInfos);

            shopController.changeTotalQuantityProductsAfterRefundedOrder(
                    new StockChangeRequest(order.getOrderUuid(), orderRequests));

            fresh.setOrderStatus(REFUNDED);
            fresh.setUpdatedAt(LocalDateTime.now());
            fresh.setPaymentId(payments.paymentId());
            fresh.setProductInfos(Collections.emptyList());
            productInfoRepository.deleteAllById(productInfos.stream().map(ProductInfo::getId).toList());

            Order savedOrder = orderRepository.save(fresh);

            OrderDtoForKafka dtoForKafka = orderMapper.toDtoForKafka(savedOrder, userMapper.toDto(savedOrder.getUser()), addressMapper.toDto(fresh.getAddress()), orderRequests);
            outboxService.saveToOutbox(dtoForKafka);
            log.info("OrderService: reconciliation: заказ {} финализирован как REFUNDED, событие зафиксировано в outbox", order.getOrderUuid());
        });
    }

    private OrderDtoForKafka toKafkaDto(Order order) {
        return orderMapper.toDtoForKafka(
                order,
                userMapper.toDto(order.getUser()),
                addressMapper.toDto(order.getAddress()),
                toOrderRequests(order.getProductInfos()));
    }
}

