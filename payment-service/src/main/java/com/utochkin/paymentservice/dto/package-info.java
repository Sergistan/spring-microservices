/**
 * Исходящие данные payment-service: {@link com.utochkin.paymentservice.dto.PaymentResponse} — результат оплаты/возврата,
 * {@link com.utochkin.paymentservice.dto.OrderPaymentsResponse} — статус платежей заказа для сверки (ReconciliationJob в order-service),
 * {@link com.utochkin.paymentservice.dto.ErrorResponse} — тело ошибки (ExceptionControllerAdvice).
 */
package com.utochkin.paymentservice.dto;
