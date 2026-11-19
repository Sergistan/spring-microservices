/**
 * Контракты входящих межсервисных вызовов shop-service (их делает order-service через OpenFeign):
 * {@link com.utochkin.shopservice.feign.OrderRequest} — позиции заказа (проверка наличия/суммы),
 * {@link com.utochkin.shopservice.feign.StockChangeRequest} — команды списать/вернуть остатки после создания/отмены заказа.
 */
package com.utochkin.shopservice.feign;
