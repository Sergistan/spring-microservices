package com.utochkin.orderservice.services;

import com.utochkin.orderservice.controllers.PaymentController;
import com.utochkin.orderservice.controllers.ShopController;
import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.dto.DeliveryStatusResponse;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.dto.UserDto;
import com.utochkin.orderservice.exceptions.DeliveryConflictException;
import com.utochkin.orderservice.exceptions.FailedOrderStatusException;
import com.utochkin.orderservice.exceptions.FailedPayOrderException;
import com.utochkin.orderservice.exceptions.OrderAccessDeniedException;
import com.utochkin.orderservice.exceptions.OrderNotFoundException;
import com.utochkin.orderservice.exceptions.ProductUnavailableException;
import com.utochkin.orderservice.exceptions.ServiceUnavailableException;
import com.utochkin.orderservice.mappers.AddressMapper;
import com.utochkin.orderservice.mappers.OrderMapper;
import com.utochkin.orderservice.mappers.ProductInfoMapper;
import com.utochkin.orderservice.mappers.UserMapper;
import com.utochkin.orderservice.models.*;
import com.utochkin.orderservice.repositories.OrderRepository;
import com.utochkin.orderservice.repositories.ProductInfoRepository;
import com.utochkin.orderservice.feign.OrderPaymentsResponse;
import com.utochkin.orderservice.web.OrderRequest;
import com.utochkin.orderservice.web.PaymentRequest;
import com.utochkin.orderservice.feign.PaymentResponse;
import com.utochkin.orderservice.feign.StockChangeRequest;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OrderServiceTest {

    @Mock
    private ShopController shopController;

    @Mock
    private PaymentController paymentController;

    @Mock
    private ProductInfoMapper productInfoMapper;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductInfoRepository productInfoRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private AddressMapper addressMapper;

    @Mock
    private OutboxService outboxService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private OrderService orderService;

    private UUID uuid;
    private Order order;
    private AddressDto addrDto;
    private List<OrderRequest> orderRequests;
    private List<ProductInfo> productInfos;
    private User user;

    @BeforeEach
    void setUp() {

        user = User.builder().id(1L).username("bob").build();
        uuid = UUID.randomUUID();

        ProductInfo pi = ProductInfo.builder()
                .id(42L)
                .articleId(UUID.fromString("bb5e2d1d-5ec2-4966-a357-25e8dc5fcfb4"))
                .quantity(2)
                .build();
        productInfos = List.of(pi);


        orderRequests = productInfos.stream()
                .map(p -> new OrderRequest(p.getArticleId(), p.getQuantity()))
                .toList();

        order = new Order();
        order.setOrderUuid(uuid);
        order.setOrderStatus(Status.WAITING_FOR_PAYMENT);
        order.setCreatedAt(LocalDateTime.now());
        order.setProductInfos(productInfos);
        order.setUser(User.builder().id(2L).subId("owner-sub").username("bob").build());
        order.setAddress(new Address());


        given(orderRepository.findByOrderUuid(uuid))
                .willReturn(Optional.of(order));
        given(orderRepository.findTotalAmountByOrderUuid(uuid))
                .willReturn(BigDecimal.valueOf(100.0));
        given(orderRepository.save(any()))
                .willAnswer(inv -> inv.getArgument(0));

        given(addressMapper.toDto(order.getAddress()))
                .willReturn(addrDto);
        given(userMapper.toDto(order.getUser()))
                .willReturn(new UserDto("bob", "", "", ""));
        given(orderMapper.toDtoForKafka(any(), any(), any(), any()))
                .willReturn(new OrderDtoForKafka(
                        uuid, BigDecimal.valueOf(100.0), Status.REFUNDED,
                        order.getCreatedAt(), LocalDateTime.now(),
                        addrDto, new UserDto("bob", "", "", ""),
                        orderRequests, UUID.randomUUID(), UUID.randomUUID(), null
                ));

        given(transactionTemplate.execute(any()))
                .willAnswer(inv -> {
                    TransactionCallback<?> callback = inv.getArgument(0);
                    return callback.doInTransaction(null);
                });
        doAnswer(inv -> {
            Consumer<org.springframework.transaction.TransactionStatus> consumer = inv.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("checkOrder → делегирует полномочия ShopController")
    void checkOrder_Delegates() {
        given(shopController.checkOrder(orderRequests)).willReturn(true);
        boolean ok = orderService.checkOrder(orderRequests);
        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("createOrder")
    void createOrder() {

        given(addressMapper.toEntity(addrDto)).willReturn(new Address());
        given(productInfoMapper.toListEntity(orderRequests)).willReturn(productInfos);
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(5L);
            return o;
        });
        given(shopController.getSumTotalPriceOrder(orderRequests)).willReturn(BigDecimal.valueOf(123.0));

        OrderDto expectedDto = new OrderDto(order.getOrderUuid(), BigDecimal.valueOf(123.0), Status.WAITING_FOR_PAYMENT,
                order.getCreatedAt(), addrDto, new UserDto("bob", "", "", ""), orderRequests
        );
        given(orderMapper.toDto(any(), any(), any(), any())).willReturn(expectedDto);


        OrderDto actual = orderService.createOrder(user, uuid, orderRequests, addrDto);

        assertThat(actual).isEqualTo(expectedDto);

        then(shopController).should().changeTotalQuantityProductsAfterCreateOrder(new StockChangeRequest(uuid, orderRequests));
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("createOrder: повторная попытка с тем же orderUuid доворячивает подтверждение списания (идемпотентность)")
    void createOrder_duplicateUuid_finalizesStockIdempotently() {
        order.setId(7L);
        given(orderRepository.saveAndFlush(any(Order.class)))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate order_uuid"));
        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));

        OrderDto expectedDto = new OrderDto(uuid, BigDecimal.valueOf(100.0), Status.WAITING_FOR_PAYMENT,
                order.getCreatedAt(), addrDto, new UserDto("bob", "", "", ""), orderRequests
        );
        given(orderMapper.toDto(any(), any(), any(), any())).willReturn(expectedDto);

        OrderDto actual = orderService.createOrder(user, uuid, orderRequests, addrDto);

        assertThat(actual).isEqualTo(expectedDto);
        then(shopController).should().changeTotalQuantityProductsAfterCreateOrder(any(StockChangeRequest.class));
        then(orderRepository).should().transitionStatus(eq(uuid), eq(List.of(Status.STOCK_PENDING)), eq(Status.WAITING_FOR_PAYMENT));
    }

    @Test
    @DisplayName("fallback createOrder: Feign 409 (недостаток товара) → ProductUnavailableException")
    void fallbackCreateOrder_feign409_productUnavailable() {
        FeignException fe = org.mockito.Mockito.mock(FeignException.class);
        org.mockito.Mockito.when(fe.status()).thenReturn(409);

        assertThrows(ProductUnavailableException.class,
                () -> orderService.fallbackMethodCreateOrder(user, uuid, orderRequests, addrDto, fe));
    }

    @Test
    @DisplayName("fallback createOrder: Feign 404 (товар не найден) → ProductUnavailableException")
    void fallbackCreateOrder_feign404_productUnavailable() {
        FeignException fe = org.mockito.Mockito.mock(FeignException.class);
        org.mockito.Mockito.when(fe.status()).thenReturn(404);

        assertThrows(ProductUnavailableException.class,
                () -> orderService.fallbackMethodCreateOrder(user, uuid, orderRequests, addrDto, fe));
    }

    @Test
    @DisplayName("fallback createOrder: прочие сбои → ServiceUnavailableException")
    void fallbackCreateOrder_otherError_serviceUnavailable() {
        assertThrows(ServiceUnavailableException.class,
                () -> orderService.fallbackMethodCreateOrder(user, uuid, orderRequests, addrDto, new RuntimeException("boom")));
    }

    @Test
    @DisplayName("paymentOrder → выдает исключение FailedOrderStatusException при уже достигнутом SUCCESS")
    void paymentOrder_AlreadySuccess_ThrowsFailedOrderStatusException() {

        order.setOrderUuid(uuid);
        order.setOrderStatus(Status.SUCCESS);

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub")
        );
        assertThat(ex.getMessage()).isEqualTo("Заказ уже оплачен!");

        then(paymentController).should(never()).paymentOrder(any());
    }

    @Test
    @DisplayName("paymentOrder → выдает исключение FailedOrderStatusException при уже достигнутом REFUNDED")
    void paymentOrder_AlreadyRefunded_ThrowsFailedOrderStatusException() {

        order.setOrderUuid(uuid);
        order.setOrderStatus(Status.REFUNDED);

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub")
        );
        assertThat(ex.getMessage()).isEqualTo("Заказ отменен, необходимо создать новый заказ!");
        then(paymentController).should(never()).paymentOrder(any());
    }

    @Test
    @DisplayName("paymentOrder → выдает исключение  OrderNotFoundException когда заказ отсутствует")
    void paymentOrder_OrderNotFound_ThrowsOrderNotFoundException() {

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.empty());

        OrderNotFoundException ex = assertThrows(
                OrderNotFoundException.class,
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub")
        );
        assertThat(ex.getMessage()).isEqualTo("Ошибка: заказ не найден!");
    }

    @Test
    @DisplayName("paymentOrder → при SUCCESS завершении возвращает PaymentResponse и отправляет в kafka")
    void paymentOrder_OnSuccess_ReturnsResponseAndSendsKafka() {

        order.setOrderUuid(uuid);
        order.setOrderStatus(Status.WAITING_FOR_PAYMENT);

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.PROCESSING))).willReturn(1);

        PaymentResponse success = new PaymentResponse(UUID.randomUUID(), Status.SUCCESS);
        given(paymentController.paymentOrder(any())).willReturn(success);
        given(orderMapper.toDtoForKafka(any(), any(), any(), any()))
                .willReturn(new OrderDtoForKafka(
                        uuid, BigDecimal.valueOf(100.0), Status.SUCCESS,
                        order.getCreatedAt(), LocalDateTime.now(),
                        addrDto, new UserDto("bob", "", "", ""), orderRequests, success.getPaymentId(), UUID.randomUUID(), null
                ));

        PaymentResponse resp = orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub");

        assertThat(resp).isEqualTo(success);
        then(outboxService).should().saveToOutbox(any(OrderDtoForKafka.class));
    }

    @Test
    @DisplayName("paymentOrder → при FAILED status выдает исключение и все равно отправляет в kafka")
    void paymentOrder_OnFailed_ThrowsAndSendsKafka() {

        order.setOrderUuid(uuid);
        order.setOrderStatus(Status.WAITING_FOR_PAYMENT);

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.PROCESSING))).willReturn(1);

        PaymentResponse failed = new PaymentResponse(UUID.randomUUID(), Status.FAILED);
        given(paymentController.paymentOrder(any())).willReturn(failed);
        given(orderMapper.toDtoForKafka(any(), any(), any(), any()))
                .willReturn(new OrderDtoForKafka(
                        uuid, BigDecimal.valueOf(100.0), Status.FAILED,
                        order.getCreatedAt(), LocalDateTime.now(),
                        addrDto, new UserDto("bob", "", "", ""), orderRequests, failed.getPaymentId(), UUID.randomUUID(), null
                ));

        assertThrows(FailedPayOrderException.class, () ->
                orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub")
        );
        then(outboxService).should().saveToOutbox(any(OrderDtoForKafka.class));
    }

    @Test
    @DisplayName("paymentOrder → CAS вернул 0 строк (конкурентная оплата) → исключение, вызова payment нет")
    void paymentOrder_casRejected_ThrowsAndNoPaymentCall() {

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.PROCESSING))).willReturn(0);

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub")
        );
        assertThat(ex.getMessage()).isEqualTo("Заказ уже оплачен!");

        then(paymentController).should(never()).paymentOrder(any());
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("paymentOrder → статус PROCESSING (повтор после сбоя) допускает повторную попытку")
    void paymentOrder_processingStatus_retryAllowed() {

        order.setOrderStatus(Status.PROCESSING);

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.PROCESSING))).willReturn(1);

        PaymentResponse success = new PaymentResponse(UUID.randomUUID(), Status.SUCCESS);
        given(paymentController.paymentOrder(any())).willReturn(success);
        given(orderMapper.toDtoForKafka(any(), any(), any(), any()))
                .willReturn(new OrderDtoForKafka(
                        uuid, BigDecimal.valueOf(100.0), Status.SUCCESS,
                        order.getCreatedAt(), LocalDateTime.now(),
                        addrDto, new UserDto("bob", "", "", ""), orderRequests, success.getPaymentId(), UUID.randomUUID(), null
                ));

        PaymentResponse resp = orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub");

        assertThat(resp.getStatus()).isEqualTo(Status.SUCCESS);
        then(paymentController).should().paymentOrder(any());
    }

    @Test
    @DisplayName("refundedOrder → OrderNotFoundException если нет заказа")
    void refundedOrder_notFound_throws() {
        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class,
                () -> orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"), "owner-sub")
        );
    }

    @ParameterizedTest(name = "refundedOrder со статусами: WAITING_FOR_PAYMENT, PROCESSING, FAILED, REFUNDED → выдает исключение FailedOrderStatusException")
    @EnumSource(value = Status.class, names = {"WAITING_FOR_PAYMENT", "PROCESSING", "FAILED", "REFUNDED"})
    void refundedOrder_invalidStatus_throws(Status badStatus) {
        order.setOrderStatus(badStatus);

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"), "owner-sub")
        );
        assertThat(ex.getMessage())
                .isEqualTo("Заказ нельзя отменить, т.к. он не был оплачен!");

        then(paymentController).should(never()).refundedOrder(any());
        then(orderRepository).should(never()).transitionStatus(any(), anyList(), any());
        then(shopController).should(never()).changeTotalQuantityProductsAfterRefundedOrder(any(StockChangeRequest.class));
        then(productInfoRepository).should(never()).deleteAllById(anyList());
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("refundedOrder → получаем статус REFUNDED")
    void refundedOrder_successfulPath() {
        order.setOrderStatus(Status.SUCCESS);
        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));
        given(orderRepository.findTotalAmountByOrderUuid(uuid)).willReturn(BigDecimal.valueOf(100.0));
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.PROCESSING))).willReturn(1);

        PaymentResponse pr = new PaymentResponse(UUID.randomUUID(), Status.REFUNDED);
        given(paymentController.refundedOrder(any())).willReturn(pr);

        orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"), "owner-sub");

        then(shopController).should()
                .changeTotalQuantityProductsAfterRefundedOrder(new StockChangeRequest(uuid,
                        List.of(new OrderRequest(
                                productInfos.get(0).getArticleId(),
                                productInfos.get(0).getQuantity()
                        ))
                ));
        then(productInfoRepository).should().deleteAllById(List.of(42L));
        then(orderRepository).should(times(1)).save(order);
        then(outboxService).should().saveToOutbox(any(OrderDtoForKafka.class));
    }

    @Test
    @DisplayName("refundedOrder → CAS вернул 0 строк (конкурентный возврат) → исключение без вызова payment")
    void refundedOrder_casRejected_ThrowsAndNoRefundCall() {
        order.setOrderStatus(Status.SUCCESS);
        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.PROCESSING))).willReturn(0);

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"), "owner-sub")
        );
        assertThat(ex.getMessage()).isEqualTo("Заказ нельзя отменить, т.к. он не был оплачен!");

        then(paymentController).should(never()).refundedOrder(any());
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("refundedOrder → когда статус не REFUNDED ничего не даем")
    void refundedOrder_nonRefundedResponse_noSideEffects() {
        order.setOrderStatus(Status.SUCCESS);
        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));
        given(orderRepository.findTotalAmountByOrderUuid(uuid)).willReturn(BigDecimal.valueOf(100.0));
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.PROCESSING))).willReturn(1);

        PaymentResponse pr = new PaymentResponse(UUID.randomUUID(), Status.FAILED);
        given(paymentController.refundedOrder(any())).willReturn(pr);

        orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"), "owner-sub");

        then(shopController).should(never()).changeTotalQuantityProductsAfterRefundedOrder(any(StockChangeRequest.class));
        then(productInfoRepository).should(never()).deleteAllById(anyList());
        then(orderRepository).should(never()).save(order);
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("reconcileStuckOrder → refunded=true → REFUNDED: восстановление остатков, удаление позиций, событие в outbox")
    void reconcileStuckOrder_refunded_finalizesRefunded() {
        order.setOrderStatus(Status.PROCESSING);
        OrderPaymentsResponse payments = new OrderPaymentsResponse(uuid, true, true, UUID.randomUUID());

        orderService.reconcileStuckOrder(order, payments);

        assertThat(order.getOrderStatus()).isEqualTo(Status.REFUNDED);
        then(shopController).should().changeTotalQuantityProductsAfterRefundedOrder(any(StockChangeRequest.class));
        then(productInfoRepository).should().deleteAllById(anyList());
        then(outboxService).should().saveToOutbox(any(OrderDtoForKafka.class));
    }

    @Test
    @DisplayName("reconcileStuckOrder → paid=true → SUCCESS: без возврата товара, событие в outbox")
    void reconcileStuckOrder_paid_finalizesSuccess() {
        order.setOrderStatus(Status.PROCESSING);
        OrderPaymentsResponse payments = new OrderPaymentsResponse(uuid, true, false, UUID.randomUUID());

        orderService.reconcileStuckOrder(order, payments);

        assertThat(order.getOrderStatus()).isEqualTo(Status.SUCCESS);
        assertThat(order.getPaymentId()).isEqualTo(payments.paymentId());
        then(shopController).should(never()).changeTotalQuantityProductsAfterRefundedOrder(any(StockChangeRequest.class));
        then(outboxService).should().saveToOutbox(any(OrderDtoForKafka.class));
    }

    @Test
    @DisplayName("reconcileStuckOrder → платежа нет → CAS PROCESSING→WAITING_FOR_PAYMENT, без событий")
    void reconcileStuckOrder_noPayment_releasesToWaiting() {
        order.setOrderStatus(Status.PROCESSING);
        OrderPaymentsResponse payments = new OrderPaymentsResponse(uuid, false, false, null);

        orderService.reconcileStuckOrder(order, payments);

        then(orderRepository).should().transitionStatus(eq(uuid), eq(List.of(Status.PROCESSING)), eq(Status.WAITING_FOR_PAYMENT));
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("reconcileStuckOrder → конкурент успел изменить статус → rollback без событий и сохранения")
    void reconcileStuckOrder_statusChangedByConcurrent_rollsBack() {
        order.setOrderStatus(Status.SUCCESS);
        OrderPaymentsResponse payments = new OrderPaymentsResponse(uuid, true, false, UUID.randomUUID());

        org.springframework.transaction.TransactionStatus txStatus = mock(org.springframework.transaction.TransactionStatus.class);
        doAnswer(inv -> {
            Consumer<org.springframework.transaction.TransactionStatus> consumer = inv.getArgument(0);
            consumer.accept(txStatus);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        orderService.reconcileStuckOrder(order, payments);

        verify(txStatus).setRollbackOnly();
        then(outboxService).should(never()).saveToOutbox(any());
        then(orderRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("createOrder: товар недоступен (Feign 409) → заказ переведён в FAILED, ProductUnavailable наружу")
    void createOrder_productUnavailable_marksFailed() {
        given(productInfoMapper.toListEntity(orderRequests)).willReturn(productInfos);
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(HttpStatus.CONFLICT.value());
        doThrow(fe).when(shopController).changeTotalQuantityProductsAfterCreateOrder(any(StockChangeRequest.class));

        assertThrows(ProductUnavailableException.class,
                () -> orderService.createOrder(user, uuid, orderRequests, addrDto));

        then(orderRepository).should().transitionStatus(uuid, List.of(Status.STOCK_PENDING), Status.FAILED);
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("paymentOrder → STOCK_PENDING отклоняется с внятным сообщением, payment не вызывается")
    void paymentOrder_stockPending_rejectedWithMessage() {
        order.setOrderStatus(Status.STOCK_PENDING);
        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub")
        );
        assertThat(ex.getMessage()).isEqualTo("Заказ ещё подтверждается, попробуйте позже");

        then(paymentController).should(never()).paymentOrder(any());
    }

    @Test
    @DisplayName("refundedOrder → STOCK_PENDING отклоняется как неоплаченный")
    void refundedOrder_stockPending_rejected() {
        order.setOrderStatus(Status.STOCK_PENDING);
        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.refundedOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub")
        );
        assertThat(ex.getMessage()).isEqualTo("Заказ нельзя отменить, т.к. он не был оплачен!");

        then(paymentController).should(never()).refundedOrder(any());
    }

    @Test
    @DisplayName("reconcile: STOCK_PENDING → повтор подтверждения списания, payment не опрашивается")
    void reconcile_stockPending_finalizesStockWithoutPaymentPoll() {
        order.setOrderStatus(Status.STOCK_PENDING);

        orderService.reconcile(order);

        then(shopController).should().changeTotalQuantityProductsAfterCreateOrder(any(StockChangeRequest.class));
        then(orderRepository).should().transitionStatus(uuid, List.of(Status.STOCK_PENDING), Status.WAITING_FOR_PAYMENT);
        then(paymentController).should(never()).getOrderPayments(any());
    }

    @Test
    @DisplayName("reconcile: STOCK_PENDING и товар недоступен → FAILED, исключение проглочено (для джобы)")
    void reconcile_stockPending_productUnavailable_marksFailed() {
        order.setOrderStatus(Status.STOCK_PENDING);
        FeignException fe = mock(FeignException.class);
        when(fe.status()).thenReturn(HttpStatus.NOT_FOUND.value());
        doThrow(fe).when(shopController).changeTotalQuantityProductsAfterCreateOrder(any(StockChangeRequest.class));

        assertThatCode(() -> orderService.reconcile(order))
                .doesNotThrowAnyException();

        then(orderRepository).should().transitionStatus(uuid, List.of(Status.STOCK_PENDING), Status.FAILED);
    }

    @Test
    @DisplayName("reconcile: PROCESSING → опрос payment и делегирование в reconcileStuckOrder")
    void reconcile_processing_pollsPaymentsAndReleasesWhenUnpaid() {
        order.setOrderStatus(Status.PROCESSING);
        given(paymentController.getOrderPayments(uuid))
                .willReturn(new OrderPaymentsResponse(uuid, false, false, null));
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.WAITING_FOR_PAYMENT))).willReturn(1);

        orderService.reconcile(order);

        then(paymentController).should().getOrderPayments(uuid);
        then(orderRepository).should().transitionStatus(eq(uuid), eq(List.of(Status.PROCESSING)), eq(Status.WAITING_FOR_PAYMENT));
    }

    @Test
    @DisplayName("paymentOrder: чужой subId → OrderAccessDeniedException, payment не вызывается")
    void paymentOrder_foreignSubId_thrownAccessDenied() {
        order.setOrderStatus(Status.WAITING_FOR_PAYMENT);

        OrderAccessDeniedException ex = assertThrows(
                OrderAccessDeniedException.class,
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "someone-else")
        );
        assertThat(ex.getMessage()).isEqualTo("Ошибка: заказ принадлежит другому пользователю");

        then(paymentController).should(never()).paymentOrder(any());
        then(orderRepository).should(never()).transitionStatus(any(), anyList(), any());
    }

    @Test
    @DisplayName("refundedOrder: чужой subId → OrderAccessDeniedException, refund не вызывается")
    void refundedOrder_foreignSubId_thrownAccessDenied() {
        order.setOrderStatus(Status.SUCCESS);

        OrderAccessDeniedException ex = assertThrows(
                OrderAccessDeniedException.class,
                () -> orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"), "someone-else")
        );
        assertThat(ex.getMessage()).isEqualTo("Ошибка: заказ принадлежит другому пользователю");

        then(paymentController).should(never()).refundedOrder(any());
        then(shopController).should(never()).changeTotalQuantityProductsAfterRefundedOrder(any(StockChangeRequest.class));
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"DELIVERING", "DELIVERED"})
    @DisplayName("refundedOrder: заказ в доставке/доставлен → отказ, refund не вызывается")
    void refundedOrder_delivering_or_delivered_rejected(Status status) {
        order.setOrderStatus(status);

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"), "owner-sub")
        );
        assertThat(ex.getMessage()).contains("возврат невозможен");

        then(paymentController).should(never()).refundedOrder(any());
        then(orderRepository).should(never()).transitionStatus(any(), anyList(), any());
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"DELIVERING", "DELIVERED"})
    @DisplayName("paymentOrder: заказ в доставке/доставлен → «уже оплачен», payment не вызывается")
    void paymentOrder_delivering_or_delivered_rejected(Status status) {
        order.setOrderStatus(status);

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"), "owner-sub")
        );
        assertThat(ex.getMessage()).isEqualTo("Заказ уже оплачен!");

        then(paymentController).should(never()).paymentOrder(any());
        then(orderRepository).should(never()).transitionStatus(any(), anyList(), any());
    }

    @Test
    @DisplayName("markOrderDelivering: CAS из SUCCESS = 1 → DELIVERING + событие в outbox")
    void markOrderDelivering_success() {
        order.setOrderStatus(Status.SUCCESS);
        given(orderRepository.transitionStatus(uuid, List.of(Status.SUCCESS), Status.DELIVERING)).willReturn(1);

        DeliveryStatusResponse response = orderService.markOrderDelivering(uuid, "Ivan Kurierov");

        assertThat(response.orderUuid()).isEqualTo(uuid);
        assertThat(response.orderStatus()).isEqualTo(Status.DELIVERING);
        then(outboxService).should().saveToOutbox(any(OrderDtoForKafka.class));
    }

    @Test
    @DisplayName("markOrderDelivering: CAS = 0 и заказ REFUNDED → 409 DeliveryConflictException (возврат успел раньше)")
    void markOrderDelivering_conflictAfterRefund() {
        order.setOrderStatus(Status.REFUNDED);
        given(orderRepository.transitionStatus(uuid, List.of(Status.SUCCESS), Status.DELIVERING)).willReturn(0);

        assertThrows(DeliveryConflictException.class, () -> orderService.markOrderDelivering(uuid, "Ivan Kurierov"));
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("markOrderDelivering: повторный вызов при DELIVERING → идемпотентный 200 без второго события")
    void markOrderDelivering_idempotentRepeat() {
        order.setOrderStatus(Status.DELIVERING);
        given(orderRepository.transitionStatus(uuid, List.of(Status.SUCCESS), Status.DELIVERING)).willReturn(0);

        DeliveryStatusResponse response = orderService.markOrderDelivering(uuid, "Ivan Kurierov");

        assertThat(response.orderStatus()).isEqualTo(Status.DELIVERING);
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("markOrderDelivered: CAS из DELIVERING = 1 → DELIVERED + событие в outbox")
    void markOrderDelivered_success() {
        order.setOrderStatus(Status.DELIVERING);
        given(orderRepository.transitionStatus(uuid, List.of(Status.DELIVERING), Status.DELIVERED)).willReturn(1);

        DeliveryStatusResponse response = orderService.markOrderDelivered(uuid);

        assertThat(response.orderStatus()).isEqualTo(Status.DELIVERED);
        then(outboxService).should().saveToOutbox(any(OrderDtoForKafka.class));
    }

    @Test
    @DisplayName("markOrderDelivered: повторный вызов при DELIVERED → идемпотентный 200")
    void markOrderDelivered_idempotentRepeat() {
        order.setOrderStatus(Status.DELIVERED);
        given(orderRepository.transitionStatus(uuid, List.of(Status.DELIVERING), Status.DELIVERED)).willReturn(0);

        DeliveryStatusResponse response = orderService.markOrderDelivered(uuid);

        assertThat(response.orderStatus()).isEqualTo(Status.DELIVERED);
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("hasActiveOrders: делегирует в репозиторий (активные = не FAILED/REFUNDED/DELIVERED)")
    void hasActiveOrders_delegates() {
        given(orderRepository.existsByUser_SubIdAndOrderStatusNotIn("sub-1", List.of(Status.FAILED, Status.REFUNDED, Status.DELIVERED)))
                .willReturn(true);

        assertThat(orderService.hasActiveOrders("sub-1")).isTrue();
        then(orderRepository).should().existsByUser_SubIdAndOrderStatusNotIn("sub-1", List.of(Status.FAILED, Status.REFUNDED, Status.DELIVERED));
    }
}
