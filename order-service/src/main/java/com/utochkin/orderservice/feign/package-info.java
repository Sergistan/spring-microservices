/**
 * Контракты исходящих межсервисных вызовов order-service (OpenFeign, резолвинг адресов через Eureka).
 *
 * {@link com.utochkin.orderservice.feign.AccountRequest} / {@link com.utochkin.orderservice.feign.PaymentResponse} /
 * {@link com.utochkin.orderservice.feign.OrderPaymentsResponse} — обмен с payment-service (оплата, возврат, сверка платежей),
 * {@link com.utochkin.orderservice.feign.StockChangeRequest} — команды shop-service списать/вернуть остатки.
 * Локальные копии контрактов нужны, чтобы order не зависел от кода других сервисов.
 */
package com.utochkin.orderservice.feign;
