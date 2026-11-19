# [“Микросервисный проект магазина товаров (spring-microservices)”](https://github.com/Sergistan/spring-microservices)

## Описание

Микросервисный проект предоставляет собой RESTful API магазина товаров, доступный через Swagger и позволяющий пользователям авторизоваться, 
создать, оплатить и вернуть заказ в магазине товаров. Админу предоставляется возможность добавлять, изменять и удалять 
товары из базы данных магазина. Получать товар/товары из магазина есть возможность как и у админа, так и у обычного пользователя.

### Схема проекта:

```mermaid
flowchart TB
    Client["Клиент<br/>Swagger UI, OAuth 2.0"] --> KC["Keycloak<br/>аутентификация, выпуск JWT"]
    Client --> GW["Getaway-server :8072<br/>проверка подписи JWT,<br/>rate limit (Redis),<br/>доверенные заголовки X-User-*"]

    GW --> ORD["Order-service :8180<br/>заказы, CAS-статусы,<br/>transactional outbox, reconciliation"]
    GW --> SHOP["Shop-service :8181<br/>товары, остатки,<br/>кэширование"]
    GW --> PAY["Payment-service :8182<br/>платежи, идемпотентность"]

    ORD -->|"Feign: проверка остатков, цена,<br/>списание и восстановление остатков"| SHOP
    ORD -->|"Feign: pay, refund, сверка<br/>JWT relay или сервисный токен"| PAY

    ORD --> PG1[("PostgreSQL<br/>orders, users,<br/>outbox_events")]
    SHOP --> PG2[("PostgreSQL<br/>products,<br/>stock_operations")]
    PAY --> PG3[("PostgreSQL<br/>accounts, payments")]
    SHOP --> R1[("Redis<br/>кэш товаров")]
    GW --> R2[("Redis<br/>rate limiter")]

    ORD -->|"OutboxRelay"| KFK{{"Kafka<br/>topic-orders"}}
    KFK --> NOTIF["Notification-service :8183<br/>письма, дедупликация<br/>по eventId (Redis)"]
    KFK --> HIST["History-service :8184<br/>история заказов, дедупликация<br/>по eventId (MongoDB)"]
    KFK -.->|"сбой после ретраев"| DLT["topic-orders.DLT<br/>мёртвая очередь"]
    NOTIF --> MAIL["SMTP Gmail"]
    HIST --> MONGO[("MongoDB")]

    subgraph INFRA["Общая инфраструктура"]
        CFG["Config-server :8071<br/>конфигурации из config-repo (GitHub),<br/>секреты в зашифрованном виде"]
        EUR["Eureka-server :8761<br/>реестр инстансов сервисов"]
    end
    CFG -.-> ORD & SHOP & PAY & NOTIF & HIST & GW
    EUR -.-> ORD & SHOP & PAY & NOTIF & HIST & GW

    subgraph OBS["Наблюдаемость"]
        ZIPKIN["Zipkin - трассировки запросов"]
        PROME["Prometheus + Grafana - метрики и дашборды"]
        ELK["ELK - сбор и поиск логов"]
    end
    ORD & SHOP & PAY & NOTIF & HIST & GW -.-> OBS
```

## Список микросервисов:

- Config-server: используется для централизованного хранения и управления конфигурацией всех микросервисов. Все конфигурации микросервисов хранятся в репозитории [config-repo](https://github.com/Sergistan/config-repo);
- Eureka-server: регистрирует и хранит актуальные сведения о доступных инстансах сервисов и предоставляет их при запросе;
- Getaway-server: осуществляется единый и централизованный доступ ко всем микросервисам, а также обеспечение безопасности;
- Order-service: сервис для создания, оплаты и отмены заказа из магазина товаров;
- Shop-service: сервис из которого берется информация о возможности создания заказа при наличии товара в магазине, а также предоставляется возможность добавлять, изменять, удалять
  и получать товар/товары из базы данных магазина;
- Payment-service: сервис через который происходит оплата или возврат заказа. Также в базе данных этого сервиса хранится информация о банковском счете пользователей, которые производят оплату или возврат товара;
- History-service: сервис сохранения истории заказов (успешные, неуспешные и отмененные заказы) в nosql бд;
- Notification-service: сервис уведомления о статусе заказов (успешные, неуспешные и отмененные заказы) через электронную почту пользователя.

## Основные технологии:

- Приложение разработано с использованием Spring Boot 3 и Java 21;
- Использован сборщик пакетов Gradle;
- Использованы sql-база данных PostgreSQL и nosql-база данных MongoDB;
- Использована система управления миграциями Liquibase;
- С помощью Swagger осуществляется отправка запросов через эндпоинты приложения;
- В order-service реализованы технологии Resilience4j (CircuitBreaker и Retry) тем самым минимизируется риск каскадных отказов и автоматически обрабатываются временные сбои;
- Регистрация, аутентификация и авторизация пользователей происходит через OAuth 2.0 с помощью Keycloak;
- Использовался Redis в микросервисе shop-service для кеширования результатов запросов и снижения нагрузки на основную БД, а также в getaway-server для rate limiting запросов и в notification-service для дедупликации событий;
- Использован брокер сообщений Kafka для одновременной и асинхронной отправки сообщений на микросервисы notification-service для уведомления пользователя о состоянии заказа и history-service для сохранения состояний заказа. Надёжность обеспечена паттерном Transactional Outbox, ручным подтверждением смещений (ack-mode MANUAL_IMMEDIATE), дедупликацией событий и мёртвой очередью topic-orders.DLT;
- Использовалась библиотека MailSender для оповещения пользователей в notification-service;
- Взаимодействие между микросервисами order-service и shop-service, а также payment-service настроено через Feign Client с обнаружением инстансов через Eureka;
- Для запуска используются Dockerfile и docker-compose;
- Код покрыт Unit-тестами с использованием JUnit5, Mockito для тестирования всех основных компонентов микросервисов, а также интеграционными тестами для каждого из микросервисов;
- Протестированы контроллеры с помощью WebMvcTest и репозитории с помощью DataJpaTest;
- Добавлены интеграционные тесты для тестирования работы кэша и отправки сообщений в брокер Kafka;
- Интеграционные тесты поднимают реальные инфраструктурные зависимости через Testcontainers (PostgreSQL, Kafka, MongoDB) — тесты работают только при запущенном Docker;
- Для отслеживания трассировки запросов через микросервисы настроен Zipkin;
- Для сбора и хранения метрик, а также визуализации этих метрик в микросервисах использованы Prometheus и Grafana;
- Для сбора, хранения и визуализации логов и событий в микросервисах использованы Elasticsearch, Logstash и Kibana;
- Для сборки, тестирования сервисов, создания и пуша образов этих сервисов в DockerHub в среде Jenkins используется Jenkinsfile.

## Надёжность и новые возможности:

Раздел описывает архитектурные улучшения, закрывающие гонки (race conditions), повышающие отказоустойчивость и безопасность системы.

### Устранение гонок и согласованность данных

- **CAS-переводы статусов заказа**: смена статуса выполняется атомарным UPDATE с условием `WHERE order_status IN (разрешённые статусы)` — два конкурирующих запроса не смогут перевести заказ дважды, проигравший получает 0 строк и корректно завершается;
- **Transactional Outbox**: событие для Kafka записывается в таблицу `outbox_events` в той же транзакции, что и статус заказа — невозможна ситуация «заказ оплачен, а событие потерялось». Фоновый OutboxRelay забирает пачки событий запросом `FOR UPDATE SKIP LOCKED` (параллельные инстансы не конфликтуют) и доставляет их в Kafka;
- **Идемпотентность платежей**: в payment-service запись о платеже защищена уникальным ограничением `(order_uuid, type)` — повторная оплата или возврат того же заказа возвращает тот же результат, а не создаёт дубль;
- **Идемпотентное списание остатков**: в shop-service каждая операция списания/восстановления остатков фиксируется маркером в `stock_operations` — повторная доставка команды (например, после сбоя сети) не изменит остаток второй раз;
- **Компенсационные откаты**: если возврат средств в payment завершился ошибкой, заказ откатывается из PROCESSING обратно в SUCCESS — заказ не «застревает» в промежуточном статусе.

### Самовосстановление (ReconciliationJob)

Фоновая задача order-service каждые 60 секунд находит заказы, зависшие в `STOCK_PENDING`/`PROCESSING` дольше 10 минут (например, после падения сервиса в середине операции), сверяется с payment-service по фактическим платежам и приводит заказ к корректному финальному статусу: финализирует SUCCESS/REFUNDED, повторяет списание остатков или откатывает в WAITING_FOR_PAYMENT.

### Безопасность

- **Защита от IDOR**: операции с заказом (оплата, возврат, просмотр) доступны только его владельцу — чужой пользователь получает 403, даже зная orderUuid;
- **Доверенные заголовки X-User-***: getaway-server после проверки подписи JWT сам проксывает в сервисы идентификацию пользователя (sub, username, email, роль) в заголовках — подделать их извне нельзя, gateway перезаписывает их своими значениями;
- **Сервисный токен (client_credentials)**: внутренние вызовы order-service к payment-service для фоновых задач получают собственный OAuth2-токен через ServiceTokenClient (с кэшем) — не нужно хранить пользовательский JWT в фоновых процессах;
- **Rate limiting**: маршруты order/shop/payment на getaway-server защищены ограничителем запросов на базе Redis (10 запросов/с, всплеск до 20; ключ — пользователь из JWT или IP для анонимов).

### Надёжная доставка в Kafka

- Потребители notification-service и history-service подтверждают смещения вручную (ack-mode MANUAL_IMMEDIATE) — только после успешной обработки;
- **Дедупликация событий**: каждый потребитель помечает обработанный eventId (Redis SETNX с TTL в notification-service, уникальный индекс в MongoDB в history-service) — при повторной доставке письмо не отправится дважды;
- **Ретраи и DLQ**: при сбое обработки (например, временный троттлинг SMTP) запись повторяется (по умолчанию 3 попытки с паузой 10 с), после чего переносится в мёртвую очередь `topic-orders.DLT` с заголовками причины — обработка остальных событий не блокируется.

### Инфраструктура

- Feign-клиенты находят сервисы через Eureka (без хардкода URL);
- Секреты не хранятся в git: ключ шифрования config-server передаётся через переменную окружения из локального `.env` (шаблон — `.env.example`), зашифрованные креды (`{cipher}...`) — в config-repo;
- Grafana при старте автоматически получает datasource Prometheus и дашборды JVM (Micrometer) и Spring Boot Statistics из каталога `grafana/provisioning` (версонируются в git);
- Ошибки запросов возвращают понятные 400-ответы с указанием проблемного поля (например, некорректный формат articleId) вместо стандартных заглушек Spring.

### Документация кода

Все классы, методы и репозитории покрыты javadoc на русском языке (назначение, гарантии целостности, потоки данных), пакеты `web`/`feign`/`dto` имеют `package-info.java` с описанием, какие данные и куда текут.

## Настройка стартовых пользователей в Keycloak:
В системе зарегистрированы 2 стартовых пользователя:  

**USERNAME:** sergistan **PASSWORD:** 111 (ADMIN)

**USERNAME:** umami **PASSWORD:** 222 (USER)

### Общая настройка Keycloak:
![Схема2](images/keycloak/realms.png)

![Схема3](images/keycloak/roles.png)

![Схема4](images/keycloak/users.png)

![Схема5](images/keycloak/spring-microservices_openId.png)

![Схема6](images/keycloak/order-service-client.png)

## Order-service API и Shop-service API в Swagger:

Эндпоинты для создания, оплаты, возврата заказов и добавления, изменения, удаления и получения товара/товаров, 
а также кнопка аутентификации посредством OAuth 2.0 с помощью Keycloak представлены в Swagger.

![Схема7](images/swagger/order-service.png)

![Схема8](images/swagger/shop-service.png)

## Основной алгоритм работы проекта:

### Создание заказа:
Клиент создает заказ для покупки товара по /order/api/v1/orders и отправляет запрос:

<details>
<summary>CompositeRequest</summary>

```json
{
  "orderRequests": [
    {
      "articleId": "3873f81b-6d10-4860-97f4-0719eb88afaa",
      "quantity": 1
    }
  ],
  "addressDto": {
    "city": "Los Angeles",
    "street": "33th Avenue",
    "houseNumber": 233,
    "apartmentNumber": 112
  }
}
```
</details>

<details>
<summary>Описание полей в CompositeRequest</summary>

| Поле | Тип | Описание |
|------|-----|----------|
| `articleId` | `UUID` (string) | Идентификатор товара |
| `quantity` | `integer` | Количество товара |
| `city` | `string` | Город |
| `street` | `string` | Улица |
| `houseNumber` | `integer` | Номер дома |
| `apartmentNumber` | `integer` | Номер квартиры|
---
</details>

Затем через FeignClient OrderService обращается к ShopService, чтобы удостовериться, что такой товар по id есть в бд магазина 
и его количество в бд позволяет создать такой заказ. Если все условия соблюдены заказ создается 
(OrderService обращается к ShopService через FeignClient для подсчета итоговой стоимости заказа) возвращается 201 с ответом:

<details>
<summary>OrderDto</summary>

```json
{
  "orderUuid": "3f9edc4b-e4cf-4257-a485-72a147a0b45f",
  "totalAmount": 1000,
  "orderStatus": "WAITING_FOR_PAYMENT",
  "createdAt": "2025-01-12 13:56",
  "addressDto": {
    "city": "Los Angeles",
    "street": "33th Avenue",
    "houseNumber": 233,
    "apartmentNumber": 112
  },
  "userDto": {
    "username": "sergistan",
    "firstName": "Сергей",
    "lastName": "Уточкин",
    "email": "dzaga73i98@gmail.com"
  },
  "orderRequests": {
    "articleId": "3873f81b-6d10-4860-97f4-0719eb88afaa",
    "quantity": 1
  }
}
```
</details>

<details>
<summary>Описание полей в OrderDto</summary>

| Поле | Тип                         | Описание |
|------|-----------------------------|----------|
| `orderUuid` | `UUID` (string)             | Уникальный идентификатор заказа |
| `totalAmount` | `double`                    | Общая сумма заказа |
| `orderStatus` | `string`                    | Статус заказа (см. «Возможные статусы заказа») |
| `createdAt` | `string (yyyy-MM-dd HH:mm)` | Дата и время создания заказа |
| `city` | `string`                    | Город доставки |
| `street` | `string`                    | Улица доставки |
| `houseNumber` | `integer`                   | Номер дома |
| `apartmentNumber` | `integer`                   | Номер квартиры (опционально) |
| `username` | `string`                    | Логин пользователя |
| `firstName` | `string`                    | Имя пользователя |
| `lastName` | `string`                    | Фамилия пользователя |
| `email` | `string`                    | Электронная почта пользователя |
| `articleId` | `UUID` (string)             | Идентификатор товара в заказе |
| `quantity` | `integer`                   | Количество товара в заказе |

</details>

<details>
<summary>Возможные статусы заказа</summary>

| Статус | Значение |
|--------|----------|
| `STOCK_PENDING` | Заказ создаётся, остатки товаров ещё не списаны |
| `WAITING_FOR_PAYMENT` | Остатки списаны, заказ ожидает оплаты клиентом |
| `PROCESSING` | Идёт операция (оплата или возврат средств) — промежуточный захват статуса |
| `SUCCESS` | Заказ успешно оплачен |
| `FAILED` | Заказ не выполнен (нет товара/остатков, не прошла оплата или списание) |
| `REFUNDED` | Оплаченный заказ отменён, средства и остатки возвращены |

Смена статусов выполняется атомарными CAS-переходами: заказ переходит из статуса в статус только из разрешённых предыдущих статусов, что исключает рассинхронизацию при конкурентных запросах.

</details>

После создания заказа в бд магазина уменьшается количество товара на количество указанное в заказе даже если заказ еще не оплачен, чтобы не было проблем 
с одновременным созданием заказов с одинаковыми товарами разными пользователями. Списание остатков идемпотентно: каждая операция фиксируется 
маркером в таблице stock_operations, поэтому повторная доставка команды (после сбоя сети или ретрая) не спишет товар дважды.

### Оплата заказа:
Оплата заказа осуществляется по /order/api/v1/orders/{orderUuid}/payments и отправляется запрос через FeignClient на PaymentService:

<details>
<summary>PaymentRequest</summary>

```json
{
  "orderUuid": "3f9edc4b-e4cf-4257-a485-72a147a0b45f",
  "cardNumber": "5078 6038 0721 8893"
}
```
</details>

<details>
<summary>Описание полей в PaymentRequest</summary>

| Поле | Тип | Описание                                 |
|------|-----|------------------------------------------|
| `orderUuid` | `UUID` (string) | Уникальный идентификатор заказа          |
| `cardNumber` | `string` | Номер банковской карты для оплаты заказа |
---
</details>

В случае ввода корректной банковской карты и при условии необходимого количества денег на ней для оплаты заказа возвращается 200 
с ответом. Оплата идемпотентна: повторный запрос по тому же заказу не спишет средства дважды — платёж защищён уникальным 
ограничением `(order_uuid, type)` в базе payment-сервиса, повторный вызов вернёт уже созданный платёж:

<details>
<summary>PaymentRequest</summary>

```json
{
  "paymentId": "3f9edc4b-e4cf-4257-a485-72a147a0b45f",
  "status": "SUCCESS"
}
```
</details>

<details>
<summary>Описание полей в PaymentRequest</summary>

| Поле | Тип | Описание                                 |
|------|-----|------------------------------------------|
| `paymentId` | `UUID` (string) | Уникальный идентификатор оплаты заказа   |
| `status` | `string` | Статус платежа (см. «Возможные статусы заказа») |
---
</details>

Если денег на счету банковской карты недостаточно для оплаты заказа, то статус меняется на FAILED.
В любом случае если оплата прошла или не прошла - OrderDtoForKafka отправляется в очередь kafka для HistoryService и NotificationService:
<details>
<summary>OrderDtoForKafka</summary>

```json
{
  "orderUuid": "3f9edc4b-e4cf-4257-a485-72a147a0b45f",
  "totalAmount": 1000,
  "orderStatus": "SUCCESS",
  "createdAt": "2025-01-12 13:56",
  "updatedAt": "2025-01-12 13:57",
  "addressDto": {
    "city": "Los Angeles",
    "street": "33th Avenue",
    "houseNumber": 233,
    "apartmentNumber": 112
  },
  "userDto": {
    "username": "sergistan",
    "firstName": "Сергей",
    "lastName": "Уточкин",
    "email": "dzaga73i98@gmail.com"
  },
  "orderRequests": {
    "articleId": "3873f81b-6d10-4860-97f4-0719eb88afaa",
    "quantity": 1
  },
  "paymentId" : "3f9edc4b-e4cf-4257-a485-72a147a0b45f",
  "eventId" : "98b0743e-64fc-4629-952f-9e3412c8c79b"
}
```
</details>

<details>
<summary>Описание полей в OrderDto</summary>

| Поле | Тип                         | Описание                                       |
|------|-----------------------------|------------------------------------------------|
| `orderUuid` | `UUID` (string)             | Уникальный идентификатор заказа                |
| `totalAmount` | `double`                    | Общая сумма заказа                             |
| `orderStatus` | `string`                    | Статус заказа (см. «Возможные статусы заказа»)  |
| `createdAt` | `string (yyyy-MM-dd HH:mm)` | Дата и время создания заказа                   |
| `updatedAt` | `string (yyyy-MM-dd HH:mm)` | Дата и время обновления заказа                 |
| `city` | `string`                    | Город доставки                                 |
| `street` | `string`                    | Улица доставки                                 |
| `houseNumber` | `integer`                   | Номер дома                                     |
| `apartmentNumber` | `integer`                   | Номер квартиры (опционально)                   |
| `username` | `string`                    | Логин пользователя                             |
| `firstName` | `string`                    | Имя пользователя                               |
| `lastName` | `string`                    | Фамилия пользователя                           |
| `email` | `string`                    | Электронная почта пользователя                 |
| `articleId` | `UUID` (string)             | Идентификатор товара в заказе                  |
| `quantity` | `integer`                   | Количество товара в заказе                     |
| `paymentId` | `UUID` (string)             | Уникальный идентификатор оплаты заказа         |
| `eventId` | `UUID` (string)             | Идентификатор события — по нему потребители дедуплицируют доставку (at-least-once не превращается в дубликаты писем/записей) |

</details>

### Отмена заказа:
Заказ можно отменить лишь при условии, что он был до этого успешно оплачен. Отменить заказ может только его владелец — 
при попытке отмены чужого заказа (по известному orderUuid) возвращается 403. Отмена заказа осуществляется по /order/api/v1/orders/{orderUuid}/refunds и отправляется запрос через FeignClient на PaymentService:

<details>
<summary>PaymentRequest</summary>

```json
{
  "orderUuid": "3f9edc4b-e4cf-4257-a485-72a147a0b45f",
  "cardNumber": "5078 6038 0721 8893"
}
```
</details>

<details>
<summary>Описание полей в PaymentRequest</summary>

| Поле | Тип | Описание                                                        |
|------|-----|-----------------------------------------------------------------|
| `orderUuid` | `UUID` (string) | Уникальный идентификатор заказа                                 |
| `cardNumber` | `string` | Номер банковской карты для возврата средств после отмены заказа |
---
</details>

В случае ввода корректной банковской карты возвращается 200 с ответом "Заказ успешно отменен". Статус в заказе меняется на REFUNDED 
и OrderDtoForKafka отправляется в очередь kafka для HistoryService и NotificationService с измененными данными.

Перед возвратом средств заказ атомарно захватывается CAS-переходом `SUCCESS → PROCESSING` (защита от одновременных возвратов). 
Если возврат средств в payment-service завершился ошибкой, заказ автоматически откатывается из PROCESSING обратно в SUCCESS — 
заказ не «застревает» в промежуточном статусе, а клиент получает понятную ошибку с причиной (например, «оплаченный заказ не найден»).

После отмены заказа в бд магазина увеличивается количество товара на количество указанное в возвращенном заказе (идемпотентно, 
по маркеру в stock_operations), а сумма средств потраченных на оплату заказа возвращается на указанную карту.

Операции по проверке, созданию, оплаты и возврата заказа выполнены с технологиями CircuitBreaker и Retry для повышения отказоустойчивости системы.

### Создание, изменение, удаление и получение товара/товаров в ShopService.
Создание, изменение, удаление и получение товара/товаров в ShopService реализованы классическими CRUD операциями. 
Единственное ограничение заключается в том, что создание, изменение и удаление товаров в магазине может осуществлять только админ.
Также над CRUD операциями установлено кэширование через Redis для повышения производительности системы.
Эндпоинт на получение всех товаров в магазине создан с применением возможности сортировки, ограничения выдачи постранично с указанием нужного размера страницы.

### NotificationService и HistoryService
Событие в формате OrderDtoForKafka отправляется в Kafka при изменении заказа на **финальный статус**:
- `SUCCESS` — заказ успешно оплачен;
- `FAILED` — оплата не прошла (например, недостаточно средств);
- `REFUNDED` — оплаченный заказ отменён.

Промежуточные состояния (`STOCK_PENDING`, `WAITING_FOR_PAYMENT`, `PROCESSING`) событиями не сопровождаются. 
Если товар недоступен на этапе создания, заказ не создаётся и событие не отправляется — клиент сразу получает 400 с причиной. 
В NotificationService с помощью JavaMailSender происходит отправка сообщений о статусе заказов через электронную почту пользователя.

![Схема9](images/после%20кафки/email.png)

В HistoryService происходит сохранение истории заказов (успешные, неуспешные и отмененные заказы) в MongoDB.

![Схема10](images/после%20кафки/mongo.png)

## Схемы взаимодействия сервисов:

Ниже — диаграммы основных сценариев. Полный happy path, сценарии сбоев и фоновые механизмы надёжности (outbox, reconciliation, DLQ).

### 1. Успешный заказ от начала до конца (happy path)

Клиент создаёт заказ, оплачивает его; событие об успешной оплате через outbox уходит в Kafka и доставляется в notification-service (письмо) и history-service (история).

```mermaid
sequenceDiagram
    autonumber
    participant C as Клиент
    participant GW as Getaway-server
    participant O as Order-service
    participant S as Shop-service
    participant P as Payment-service
    participant K as Kafka
    participant N as Notification-service
    participant H as History-service

    C->>GW: POST /order/api/v1/orders + JWT
    GW->>GW: Проверка подписи JWT, rate limit, перезапись X-User-*
    GW->>O: Запрос + доверенные заголовки X-User-*
    O->>S: проверка остатков (stock-verifications)
    S-->>O: true
    O->>S: Списание остатков - идемпотентно, по маркеру
    O-->>C: 201 OrderDto в статусе WAITING_FOR_PAYMENT

    C->>GW: POST /order/api/v1/orders/{uuid}/payments - карта
    GW->>O: Запрос + X-User-* (владелец проверяется)
    O->>O: CAS захват - WAITING_FOR_PAYMENT -> PROCESSING
    O->>P: pay - сумма и карта
    P->>P: Атомарное списание + запись PAYMENT - уникальность order_uuid+type
    P-->>O: SUCCESS + paymentId
    O->>O: Одна транзакция - статус SUCCESS и событие в outbox_events
    Note over O,K: OutboxRelay доставляет событие в Kafka
    O--)K: OrderDtoForKafka - ключ orderUuid, поле eventId
    K--)N: Дедупликация по eventId, письмо об успешной оплате
    K--)H: Дедупликация по eventId, запись в MongoDB
    O-->>C: 200 SUCCESS + paymentId
```

### 2. Товара нет или не хватает остатка

Проверка остатков (checkOrder) возвращает false — заказ не создаётся вовсе, клиент сразу получает понятную ошибку. Событие в Kafka не отправляется: финального исхода заказа ещё нет.

```mermaid
sequenceDiagram
    autonumber
    participant C as Клиент
    participant GW as Getaway-server
    participant O as Order-service
    participant S as Shop-service

    C->>GW: POST /order/api/v1/orders
    GW->>O: Запрос + X-User-*
    O->>S: проверка остатков по списку articleId
    S-->>O: false - товара нет или остатка недостаточно
    Note over O: Заказ не создаётся, событие в Kafka не отправляется
    O-->>C: 400 - Товар отсутствует в магазине или его количество недостаточно
```

### 3. Недостаточно средств при оплате

Оплата отклоняется payment-service, списанные при создании остатки возвращаются в магазин, заказ — FAILED.

```mermaid
sequenceDiagram
    autonumber
    participant C as Клиент
    participant O as Order-service
    participant P as Payment-service
    participant S as Shop-service
    participant K as Kafka

    C->>O: POST /order/api/v1/orders/{uuid}/payments
    O->>O: CAS захват - WAITING_FOR_PAYMENT -> PROCESSING
    O->>P: pay
    P-->>O: 402 - на карте недостаточно средств
    O->>S: Возврат списанных остатков - идемпотентно
    O->>O: Компенсация статуса - PROCESSING -> FAILED
    O--)K: Событие FAILED
    O-->>C: 402 - не хватает средств
```

### 4. Возврат оплаченного заказа

Только владелец и только из SUCCESS; при сбое возврата средств заказ откатывается назад — застревание исключено.

```mermaid
sequenceDiagram
    autonumber
    participant C as Клиент
    participant O as Order-service
    participant P as Payment-service
    participant S as Shop-service
    participant K as Kafka

    C->>O: POST /order/api/v1/orders/{uuid}/refunds
    O->>O: Проверки - владелец по X-User-SubId и статус SUCCESS
    alt Проверки пройдены
        O->>O: CAS захват - SUCCESS -> PROCESSING
        O->>P: refund - возврат на карту
        alt Возврат прошёл
            P-->>O: REFUNDED
            O->>S: Восстановление остатков - идемпотентно
            O->>O: Статус REFUNDED и событие в outbox
            O--)K: Событие REFUNDED - письмо и история
            O-->>C: 200 - Заказ успешно отменен
        else Сбой возврата - платёж не найден и прочее
            O->>O: Компенсация - PROCESSING -> SUCCESS
            O-->>C: Ошибка с причиной отказа payment
        end
    else Заказ не оплачен или не владелец
        O-->>C: 400 - не был оплачен или 403 - чужой заказ
    end
```

### 5. Transactional Outbox — гарантия доставки события

Событие и статус заказа фиксируются в одной транзакции БД; доставка в Kafka — отдельный фоновый процесс.

```mermaid
flowchart LR
    A["Транзакция БД order-service:<br/>статус заказа + запись в outbox_events"] --> B["OutboxRelay - каждую секунду<br/>пачки по 100 записей<br/>SELECT FOR UPDATE SKIP LOCKED"]
    B --> C["Отправка в Kafka<br/>ключ = orderUuid"]
    C -->|успех| D["Удаление записи из outbox"]
    C -->|сбой| E["Запись остаётся -<br/>повторная попытка в следующем такте"]
```

### 6. ReconciliationJob — самовосстановление застрявших заказов

Джоба каждые 60 секунд находит заказы, зависшие дольше 10 минут, и по фактическому состоянию payment приводит их к корректному статусу.

```mermaid
flowchart TD
    A["ReconciliationJob - каждые 60 с<br/>заказы в STOCK_PENDING / PROCESSING<br/>не обновлявшиеся более 10 минут"] --> B{"Сверка с payment-service:<br/>платежи по заказу"}
    B -->|есть REFUND| C["Финализация REFUNDED<br/>+ восстановление остатков"]
    B -->|есть PAYMENT SUCCESS| D["Финализация SUCCESS<br/>+ событие в outbox"]
    B -->|платежей нет| E{"Текущий статус заказа"}
    E -->|STOCK_PENDING| F["Повторная попытка списания остатков<br/>товара нет - заказ в FAILED"]
    E -->|PROCESSING| G["CAS откат в WAITING_FOR_PAYMENT"]
```

### 7. DLQ — сбой обработки не блокирует очередь

При ошибке обработки (например, временный троттлинг SMTP) запись ретраится и при исчерпании попыток уходит в мёртвую очередь.

```mermaid
flowchart LR
    A["Сообщение из topic-orders"] --> B{"Обработка успешна?"}
    B -->|да| C["ack - MANUAL_IMMEDIATE"]
    B -->|нет| D["Ретраи - по умолчанию<br/>3 попытки с паузой 10 с"]
    D --> B2{"Успешно после ретраев?"}
    B2 -->|да| C
    B2 -->|нет| E["topic-orders.DLT<br/>заголовки kafka_dlt-exception с причиной"]
    E -.-> F["Остальные события очереди<br/>обрабатываются дальше"]
```

### 8. Аутентификация и доверие между сервисами

Gateway — единственная точка, где из JWT формируются доверенные заголовки; фоновые вызовы используют сервисный токен.

```mermaid
sequenceDiagram
    autonumber
    participant U as Пользователь
    participant GW as Getaway-server
    participant KC as Keycloak
    participant O as Order-service
    participant P as Payment-service

    U->>KC: Вход через OAuth 2.0
    KC-->>U: JWT access token
    U->>GW: API-запрос с Bearer JWT
    GW->>GW: Проверка подписи RSA - rate limit Redis<br/>перезапись X-User-SubId UserName Email Role
    GW->>O: Запрос + X-User-* - извне не подделать
    O->>O: Проверка владельца заказа по X-User-SubId
    Note over O,P: Внутренние вызовы order к payment:<br/>пользовательский JWT проксируется интерцептором,<br/>фоновые задачи - сервисный токен client_credentials из ServiceTokenClient
    O->>P: pay / refund / getOrderPayments
```

## Инструменты для просмотра метрик, логов и трассеров в проекте:

ELK - система для сбора, хранения и мониторинга логов в системе.

![Схема11](images/мониторинг/elk.jpg)

Prometheus и Grafana - для сбора и визуализации метрик.

![Схема12](images/мониторинг/grafana.png)

Zipkin - для сбора и визуализации трассировки между микросервисами.

![Схема13](images/мониторинг/zipkin.png)

## CI/CD

CI/CD реализован в среде Jenkins для сборки, тестирования сервисов, создания и пуша образов этих сервисов в DockerHub. 

![Схема14](images/jenkins.png)