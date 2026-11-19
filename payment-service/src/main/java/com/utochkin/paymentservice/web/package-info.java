/**
 * Входящие REST-контракты payment-service: {@link com.utochkin.paymentservice.web.AccountRequest} —
 * тело запросов /pay и /refunded (uuid заказа, сумма, номер карты). Приходит от order-service через Eureka.
 */
package com.utochkin.paymentservice.web;
