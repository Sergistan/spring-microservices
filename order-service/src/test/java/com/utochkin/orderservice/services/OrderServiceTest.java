package com.utochkin.orderservice.services;

import com.utochkin.orderservice.controllers.PaymentController;
import com.utochkin.orderservice.controllers.ShopController;
import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.dto.OrderDtoForKafka;
import com.utochkin.orderservice.dto.UserDto;
import com.utochkin.orderservice.exceptions.FailedOrderStatusException;
import com.utochkin.orderservice.exceptions.FailedPayOrderException;
import com.utochkin.orderservice.exceptions.OrderNotFoundException;
import com.utochkin.orderservice.mappers.AddressMapper;
import com.utochkin.orderservice.mappers.OrderMapper;
import com.utochkin.orderservice.mappers.ProductInfoMapper;
import com.utochkin.orderservice.mappers.UserMapper;
import com.utochkin.orderservice.models.*;
import com.utochkin.orderservice.repositories.OrderRepository;
import com.utochkin.orderservice.repositories.ProductInfoRepository;
import com.utochkin.orderservice.request.OrderRequest;
import com.utochkin.orderservice.request.PaymentRequest;
import com.utochkin.orderservice.request.PaymentResponse;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

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
        order.setUser(new User());
        order.setAddress(new Address());


        given(orderRepository.findByOrderUuid(uuid))
                .willReturn(Optional.of(order));
        given(orderRepository.findTotalAmountByOrderUuid(uuid))
                .willReturn(100.0);
        given(orderRepository.save(any()))
                .willAnswer(inv -> inv.getArgument(0));

        given(addressMapper.toDto(order.getAddress()))
                .willReturn(addrDto);
        given(userMapper.toDto(order.getUser()))
                .willReturn(new UserDto("bob", "", "", ""));
        given(orderMapper.toDtoForKafka(any(), any(), any(), any()))
                .willReturn(new OrderDtoForKafka(
                        uuid, 100.0, Status.REFUNDED,
                        order.getCreatedAt(), LocalDateTime.now(),
                        addrDto, new UserDto("bob", "", "", ""),
                        orderRequests, UUID.randomUUID(), UUID.randomUUID()
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
        given(orderRepository.save(any(Order.class))).willAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(5L);
            return o;
        });
        given(shopController.getSumTotalPriceOrder(orderRequests)).willReturn(123.0);

        OrderDto expectedDto = new OrderDto(
                order.getOrderUuid(), 123.0, Status.WAITING_FOR_PAYMENT,
                order.getCreatedAt(), addrDto, new UserDto("bob", "", "", ""), orderRequests
        );
        given(orderMapper.toDto(any(), any(), any(), any())).willReturn(expectedDto);


        OrderDto actual = orderService.createOrder(user, orderRequests, addrDto);

        assertThat(actual).isEqualTo(expectedDto);

        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("paymentOrder → выдает исключение FailedOrderStatusException при уже достигнутом SUCCESS")
    void paymentOrder_AlreadySuccess_ThrowsFailedOrderStatusException() {

        order.setOrderUuid(uuid);
        order.setOrderStatus(Status.SUCCESS);

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"))
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
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"))
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
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"))
        );
        assertThat(ex.getMessage()).isEqualTo("Error: order not found!");
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
                        uuid, 100.0, Status.SUCCESS,
                        order.getCreatedAt(), LocalDateTime.now(),
                        addrDto, new UserDto("bob", "", "", ""), orderRequests, success.getPaymentId(), UUID.randomUUID()
                ));

        PaymentResponse resp = orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"));

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
                        uuid, 100.0, Status.FAILED,
                        order.getCreatedAt(), LocalDateTime.now(),
                        addrDto, new UserDto("bob", "", "", ""), orderRequests, failed.getPaymentId(), UUID.randomUUID()
                ));

        assertThrows(FailedPayOrderException.class, () ->
                orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"))
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
                () -> orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"))
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
                        uuid, 100.0, Status.SUCCESS,
                        order.getCreatedAt(), LocalDateTime.now(),
                        addrDto, new UserDto("bob", "", "", ""), orderRequests, success.getPaymentId(), UUID.randomUUID()
                ));

        PaymentResponse resp = orderService.paymentOrder(new PaymentRequest(uuid, "1111 2222 3333 4444"));

        assertThat(resp.getStatus()).isEqualTo(Status.SUCCESS);
        then(paymentController).should().paymentOrder(any());
    }

    @Test
    @DisplayName("refundedOrder → OrderNotFoundException если нет заказа")
    void refundedOrder_notFound_throws() {
        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class,
                () -> orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"))
        );
    }

    @ParameterizedTest(name = "refundedOrder со статусами: WAITING_FOR_PAYMENT, PROCESSING, FAILED, REFUNDED → выдает исключение FailedOrderStatusException")
    @EnumSource(value = Status.class, names = {"WAITING_FOR_PAYMENT", "PROCESSING", "FAILED", "REFUNDED"})
    void refundedOrder_invalidStatus_throws(Status badStatus) {
        order.setOrderStatus(badStatus);

        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));

        FailedOrderStatusException ex = assertThrows(
                FailedOrderStatusException.class,
                () -> orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"))
        );
        assertThat(ex.getMessage())
                .isEqualTo("Заказ нельзя отменить, т.к. он не был оплачен!");

        then(paymentController).should(never()).refundedOrder(any());
        then(orderRepository).should(never()).transitionStatus(any(), anyList(), any());
        then(shopController).should(never()).changeTotalQuantityProductsAfterRefundedOrder(anyList());
        then(productInfoRepository).should(never()).deleteAllById(anyList());
        then(outboxService).should(never()).saveToOutbox(any());
    }

    @Test
    @DisplayName("refundedOrder → получаем статус REFUNDED")
    void refundedOrder_successfulPath() {
        order.setOrderStatus(Status.SUCCESS);
        given(orderRepository.findByOrderUuid(uuid)).willReturn(Optional.of(order));
        given(orderRepository.findTotalAmountByOrderUuid(uuid)).willReturn(100.0);
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.PROCESSING))).willReturn(1);

        PaymentResponse pr = new PaymentResponse(UUID.randomUUID(), Status.REFUNDED);
        given(paymentController.refundedOrder(any())).willReturn(pr);

        orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"));

        then(shopController).should()
                .changeTotalQuantityProductsAfterRefundedOrder(
                        List.of(new OrderRequest(
                                productInfos.get(0).getArticleId(),
                                productInfos.get(0).getQuantity()
                        ))
                );
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
                () -> orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"))
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
        given(orderRepository.findTotalAmountByOrderUuid(uuid)).willReturn(100.0);
        given(orderRepository.transitionStatus(eq(uuid), anyList(), eq(Status.PROCESSING))).willReturn(1);

        PaymentResponse pr = new PaymentResponse(UUID.randomUUID(), Status.FAILED);
        given(paymentController.refundedOrder(any())).willReturn(pr);

        orderService.refundedOrder(new PaymentRequest(uuid, "5078 6038 0721 8893"));

        then(shopController).should(never()).changeTotalQuantityProductsAfterRefundedOrder(anyList());
        then(productInfoRepository).should(never()).deleteAllById(anyList());
        then(orderRepository).should(never()).save(order);
        then(outboxService).should(never()).saveToOutbox(any());
    }
}
