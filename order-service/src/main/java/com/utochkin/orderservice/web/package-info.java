/**
 * Входящие REST-контракты order-service: тела запросов, которые принимает {@link com.utochkin.orderservice.controllers.OrderController}.
 *
 * {@link com.utochkin.orderservice.web.CompositeRequest} — создание заказа (позиции + адрес доставки),
 * {@link com.utochkin.orderservice.web.PaymentRequest} — оплата/возврат (uuid заказа + номер карты).
 * Данные приходят с getaway-server и уже обогащены заголовками X-User-* (из JWT пользователя).
 */
package com.utochkin.orderservice.web;
