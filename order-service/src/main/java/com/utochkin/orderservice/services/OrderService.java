package com.utochkin.orderservice.services;


import com.utochkin.orderservice.controllers.PaymentController;
import com.utochkin.orderservice.controllers.ShopController;
import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.dto.DeliveryStatusResponse;
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
import com.utochkin.orderservice.feign.AccountRequest;
import com.utochkin.orderservice.feign.OrderPaymentsResponse;
import com.utochkin.orderservice.web.OrderRequest;
import com.utochkin.orderservice.web.PaymentRequest;
import com.utochkin.orderservice.feign.PaymentResponse;
import com.utochkin.orderservice.feign.StockChangeRequest;
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


/**
 * Оркестрация жизненного цикла заказа: создание, оплата, возврат, сверка застрявших заказов.
 *
 * Ключевые механики:
 *  - createOrder — 3 шага: INSERT со статусом STOCK_PENDING -> атомарное списание остатков в shop
 *    -> CAS-переключение в WAITING_FOR_PAYMENT; если списание упало — заказ остаётся в STOCK_PENDING,
 *    и его подберёт ReconciliationJob (защита от «осиротевших» списаний);
 *  - paymentOrder/refundedOrder — проверка владельца (X-User-SubId, чужим 403), CAS-статусы
 *    (конкурентная оплата одного заказа невозможна), идемпотентные вызовы payment;
 *  - reconcile — сверка застрявшего заказа с payment (оплачен/возвращён/платежа нет)
 *    с фиксацией финального статуса или откатом списания остатков.
 *
 * Данные: PostgreSQL (orders, outbox), Feign -> shop-service / payment-service (через Eureka);
 * события заказа публикуются в Kafka опосредованно — через OutboxService/OutboxRelay.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class OrderService {

    private static final List<Status> PAYABLE_STATUSES = List.of(Status.WAITING_FOR_PAYMENT, Status.FAILED, Status.PROCESSING);
    private static final List<Status> REFUNDABLE_STATUSES = List.of(Status.SUCCESS);
    /** Статусы, после которых заказ завершён окончательно: ни оплаты, ни возврата, ни доставки уже не будет. */
    private static final List<Status> FINAL_STATUSES = List.of(Status.FAILED, Status.REFUNDED, Status.DELIVERED);

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

    /**
     * Фиксация результата платежа (pay/refund) в одной транзакции: обновить статус и paymentId
     * заказа, собрать событие OrderDtoForKafka и записать его в outbox (публикация — OutboxRelay).
     */
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

    /**
     * Проверка наличия всех товаров заказа в shop-service (Feign, с retry и circuit breaker).
     * Fallback превращает недоступность shop в понятную 503-ошибку пользователю.
     */
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "circuitBreakerCheckOrder", fallbackMethod = "fallbackMethodCheckOrder")
    @Retry(name = "retryCheckOrder", fallbackMethod = "fallbackMethodCheckOrder")
    public Boolean checkOrder(List<OrderRequest> orderRequests) {
        log.info("OrderService: проверка наличия товаров: {}", orderRequests);
        return shopController.checkOrder(orderRequests);
    }

    /** Fallback checkOrder: исчерпаны retry/circuit breaker — сервис магазина недоступен. */
    public Boolean fallbackMethodCheckOrder(List<OrderRequest> orderRequests, Throwable throwable) {
        log.error("Fallback для checkOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }

    /**
     * Создание заказа в 3 шага (защита от осиротевших списаний):
     *  1) сумма по позициям у shop-service;
     *  2) INSERT заказа со статусом STOCK_PENDING + позиции (DataIntegrityViolation = повтор
     *     с тем же orderUuid от предыдущей попытки — идемпотентно продолжаем);
     *  3) finalizeStock — списание остатков в shop и CAS-переход в WAITING_FOR_PAYMENT.
     */
    @CircuitBreaker(name = "circuitBreakerCreateOrder", fallbackMethod = "fallbackMethodCreateOrder")
    @Retry(name = "retryCreateOrder", fallbackMethod = "fallbackMethodCreateOrder")
    public OrderDto createOrder(User user, UUID orderUuid, List<OrderRequest> orderRequests, AddressDto addressDto) {
        log.info("OrderService: начало создания заказа {} для user={} requests={}", orderUuid, user.getUsername(), orderRequests);

        BigDecimal totalAmount = shopController.getSumTotalPriceOrder(orderRequests);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                // Шаг 1: фиксируем заказ «просечно» — до списания остатков
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

    /**
     * Шаг 3 создания: списать остатки в shop и перевести заказ STOCK_PENDING -> WAITING_FOR_PAYMENT.
     * Товар кончился (409/404 от shop): заказ атомарно переводится в FAILED (rollback-guard:
     * transitionStatus сработает только если статус ещё STOCK_PENDING).
     */
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

    /** Fallback createOrder: бизнес-ошибки (товара нет) пробрасываем как есть, прочее — 503. */
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

    /**
     * Оплата заказа: проверка владельца (X-User-SubId, чужим 403) -> запрет повторной оплаты
     * по статусу -> CAS-захват PROCESSING (transitionStatus по PAYABLE_STATUSES; 0 строк =
     * конкурент уже оплатил) -> вызов payment -> finalizePaymentResult (статус + событие в outbox).
     * FAILED-ответ payment пробрасываем как FailedPayOrderException (HTTP клиенту).
     */
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
            case SUCCESS, DELIVERING, DELIVERED -> throw new FailedOrderStatusException("Заказ уже оплачен!");
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

    /** Fallback paymentOrder: бизнес-ошибки payment пробрасываем, технические — 503. */
    public PaymentResponse fallbackMethodPayOrder(PaymentRequest paymentRequest, String subId, Throwable throwable) {
        extractedFullbackMethod(paymentRequest, throwable);
        log.error("Fallback для paymentOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }


    /**
     * Возврат оплаченного заказа: проверка владельца (403 чужим) -> только статус SUCCESS
     * годен -> CAS-захват PROCESSING -> возврат средств в payment -> в одной транзакции:
     * вернуть остатки в shop, удалить позиции, статус REFUNDED, событие в outbox.
     * Компенсация: если payment ответил ошибкой, PROCESSING откатывается в SUCCESS
     * (иначе заказ застревает и последующие попытки получают "не был оплачен").
     */
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
            case DELIVERING, DELIVERED ->
                    throw new FailedOrderStatusException("Заказ уже передан в доставку или доставлен — возврат невозможен!");
        }

        Integer acquired = transactionTemplate.execute(status ->
                orderRepository.transitionStatus(paymentRequest.getOrderUuid(), REFUNDABLE_STATUSES, Status.PROCESSING));
        if (acquired == null || acquired == 0) {
            throw new FailedOrderStatusException("Заказ нельзя отменить, т.к. он не был оплачен!");
        }

        PaymentResponse refundedOrder;
        try {
            BigDecimal totalAmountById = orderRepository.findTotalAmountByOrderUuid(paymentRequest.getOrderUuid());
            refundedOrder = paymentController.refundedOrder(new AccountRequest(
                    paymentRequest.getOrderUuid(), totalAmountById, paymentRequest.getCardNumber()));
        } catch (RuntimeException e) {
            // Компенсация неудачного возврата: вернуть статус SUCCESS, чтобы заказ не застревал
            // в PROCESSING и повторная попытка начиналась с корректного состояния
            Integer restored = transactionTemplate.execute(status ->
                    orderRepository.transitionStatus(paymentRequest.getOrderUuid(), List.of(Status.PROCESSING), Status.SUCCESS));
            if (restored != null && restored > 0) {
                log.warn("OrderService: возврат не удался ({}), заказ {} возвращён в SUCCESS",
                        e.getMessage(), paymentRequest.getOrderUuid());
            }
            throw e;
        }

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

    /** Fallback refundedOrder: карта не найдена/нет средств/неверный статус — как есть, прочее — 503. */
    public void fallbackMethodRefundedOrder(PaymentRequest paymentRequest, String subId, Throwable throwable) {
        extractedFullbackMethod(paymentRequest, throwable);
        log.error("Fallback для refundedOrder сработал из-за: {}", throwable.getMessage());
        throw new ServiceUnavailableException("Сервис временно недоступен, пожалуйста, повторите попытку позже");
    }

    /**
     * Перевод оплаченного заказа в DELIVERING — внутренний вызов delivery-service
     * (сервисный токен, проверка azp в контроллере) через 5 минут после оплаты.
     * CAS из SUCCESS: гонку с возвратом решает владелец статуса (order-service) —
     * 0 строк означает, что конкурент (refundedOrder) уже увёл заказ из SUCCESS.
     * Повтор при DELIVERING/DELIVERED идемпотентен (200 без второго события),
     * прочие статусы — 409 (доставка обязана отменить свою сторону и освободить курьера).
     * Событие DELIVERING фиксируется в outbox в одной транзакции с переводом.
     */
    public DeliveryStatusResponse markOrderDelivering(UUID orderUuid) {
        orderRepository.findByOrderUuid(orderUuid).orElseThrow(OrderNotFoundException::new);
        Boolean applied = transactionTemplate.execute(txStatus -> {
            Integer updated = orderRepository.transitionStatus(orderUuid, List.of(Status.SUCCESS), Status.DELIVERING);
            if (updated != null && updated > 0) {
                Order fresh = orderRepository.findByOrderUuid(orderUuid).orElseThrow(OrderNotFoundException::new);
                outboxService.saveToOutbox(toKafkaDto(fresh));
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(applied)) {
            log.info("OrderService: заказ {} передан в доставку (DELIVERING), событие зафиксировано в outbox", orderUuid);
            return new DeliveryStatusResponse(orderUuid, Status.DELIVERING);
        }
        return resolveDeliveryTransition(orderUuid, Status.DELIVERING, Status.DELIVERED);
    }

    /**
     * Фиксация вручения заказа покупателю — внутренний вызов delivery-service
     * по завершении доставки. CAS из DELIVERING; повтор при DELIVERED идемпотентен,
     * прочие статусы — 409. Событие DELIVERED — через outbox (письмо «доставлен»).
     */
    public DeliveryStatusResponse markOrderDelivered(UUID orderUuid) {
        orderRepository.findByOrderUuid(orderUuid).orElseThrow(OrderNotFoundException::new);
        Boolean applied = transactionTemplate.execute(txStatus -> {
            Integer updated = orderRepository.transitionStatus(orderUuid, List.of(Status.DELIVERING), Status.DELIVERED);
            if (updated != null && updated > 0) {
                Order fresh = orderRepository.findByOrderUuid(orderUuid).orElseThrow(OrderNotFoundException::new);
                outboxService.saveToOutbox(toKafkaDto(fresh));
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(applied)) {
            log.info("OrderService: заказ {} доставлен покупателю (DELIVERED), событие зафиксировано в outbox", orderUuid);
            return new DeliveryStatusResponse(orderUuid, Status.DELIVERED);
        }
        return resolveDeliveryTransition(orderUuid, Status.DELIVERED);
    }

    /** Разбор CAS=0 для доставки: идемпотентный повтор (уже в целевом статусе) или 409-конфликт. */
    private DeliveryStatusResponse resolveDeliveryTransition(UUID orderUuid, Status... alreadyAppliedStatuses) {
        Status current = orderRepository.findByOrderUuid(orderUuid).orElseThrow(OrderNotFoundException::new).getOrderStatus();
        for (Status applied : alreadyAppliedStatuses) {
            if (current == applied) {
                log.info("OrderService: заказ {} уже в статусе {} — идемпотентный повтор доставки", orderUuid, current);
                return new DeliveryStatusResponse(orderUuid, current);
            }
        }
        throw new DeliveryConflictException(current);
    }

    /**
     * Есть ли у пользователя незавершённые заказы (не FAILED/REFUNDED/DELIVERED) —
     * внутренний вызов auth-service при удалении аккаунта: активные заказы
     * (включая SUCCESS — после оплаты предстоит доставка) запрещают удаление.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveOrders(String subId) {
        return orderRepository.existsByUser_SubIdAndOrderStatusNotIn(subId, FINAL_STATUSES);
    }

    /**
     * Общий разбор ошибок для fallback'ов pay/refund: переводит Feign-статусы payment-сервиса
     * в доменные исключения (404 -> карта не найдена, 402 -> не хватает средств,
     * 400 -> причина из тела ErrorResponse payment, например "возврат невозможен"),
     * а доменные исключения пробрасывает без двойной обёртки.
     */
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
            if (fe.status() == HttpStatus.BAD_REQUEST.value()) {
                // payment вернул бизнес-отказ (например, "оплаченный заказ не найден, возврат
                // невозможен") — показываем его причину, а не безликое "сервис недоступен"
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\"messageError\"\\s*:\\s*\"([^\"]+)\"")
                        .matcher(fe.contentUTF8());
                String reason = m.find() ? m.group(1) : "Платёжный сервис отклонил операцию";
                log.warn("Payment отклонил операцию для заказа {}: {}", paymentRequest.getOrderUuid(), reason);
                throw new FailedOrderStatusException(reason);
            }
        }
        if (throwable instanceof FailedOrderStatusException ||
                throwable instanceof CardNumberNotFoundException ||
                throwable instanceof FailedPayOrderException ||
                throwable instanceof OrderNotFoundException) {
            throw (RuntimeException) throwable;
        }
    }

    /** Маскировка номера карты для логов: видны только последние 4 цифры. */
    private static String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    /**
     * Точка входа сверки (вызывает ReconciliationJob): STOCK_PENDING — повторить попытку
     * списания (создание оборвалось до finalizeStock); PROCESSING — спросить payment
     * о платежах и финализировать/расфиксировать заказ (reconcileStuckOrder).
     */
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

    /** Positions заказа -> List<OrderRequest> (для команд shop и событий Kafka). */
    private List<OrderRequest> toOrderRequests(List<ProductInfo> productInfos) {
        return productInfos.stream()
                .map(productInfo -> new OrderRequest(productInfo.getArticleId(), productInfo.getQuantity()))
                .toList();
    }

    /**
     * Решение по застрявшему в PROCESSING заказу по ответу payment:
     * refund был -> REFUNDED (+возврат остатков), платёж есть -> SUCCESS,
     * платежа нет -> CAS-возврат в WAITING_FOR_PAYMENT (0 строк = кто-то уже сдвинул).
     */
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

    /**
     * Сверка «платёж прошёл»: фиксируем SUCCESS + событие в outbox.
     * Rollback-guard: если статус уже не PROCESSING (кто-то успел) — откат без изменений.
     */
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

    /**
     * Сверка «возврат прошёл»: вернуть остатки в shop, удалить позиции, REFUNDED + событие.
     * Rollback-guard аналогично finalizeReconciledSuccess.
     */
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

    /** Сборка события OrderDtoForKafka из сохранённого заказа (мапперы user/address/positions). */
    private OrderDtoForKafka toKafkaDto(Order order) {
        return orderMapper.toDtoForKafka(
                order,
                userMapper.toDto(order.getUser()),
                addressMapper.toDto(order.getAddress()),
                toOrderRequests(order.getProductInfos()));
    }
}

