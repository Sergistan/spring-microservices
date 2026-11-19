import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.orderservice.OrderServiceApplication;
import com.utochkin.orderservice.controllers.PaymentController;
import com.utochkin.orderservice.controllers.ShopController;
import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.dto.OrderDto;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.models.User;
import com.utochkin.orderservice.repositories.OutboxRepository;
import com.utochkin.orderservice.web.*;
import com.utochkin.orderservice.feign.*;
import com.utochkin.orderservice.services.OrderService;
import com.utochkin.orderservice.services.OutboxRelay;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;

@SpringBootTest(
        classes = OrderServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.cloud.compatibility-verifier.enabled=false",
                "spring.liquibase.enabled=false",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
                "outbox.relay.fixed-delay=3600000"
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@EnableAutoConfiguration(exclude = {CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class})
class OrderServiceIntegrationTest {

    // Реальный PostgreSQL в docker-контейнере: @ServiceConnection подставляет реквизиты
    // dataSource в тестовый контекст (вместо встроенной H2; схема создаётся Hibernate)
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    // Реальный Kafka-брокер в контейнере (вместо EmbeddedKafka):
    // @ServiceConnection отдаёт bootstrap-servers и продюсеру, и консьюмеру этого теста
    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OrderService orderService;

    @MockitoBean
    private ShopController shopController;
    @MockitoBean
    private PaymentController paymentController;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    // Хелпер: создаёт заказ через REST, подменяя ответы shop-сервиса моками
    // (checkOrder=true, сумма заказа = total); возвращает orderUuid из ответа
    private UUID createOrder(BigDecimal total) throws Exception {
        UUID articleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<OrderRequest> req = List.of(new OrderRequest(articleId, 3));
        given(shopController.checkOrder(req)).willReturn(true);
        given(shopController.getSumTotalPriceOrder(req)).willReturn(total);

        CompositeRequest cr = new CompositeRequest(req, new AddressDto("City", "Street", 10, 101));
        String json = objectMapper.writeValueAsString(cr);

        String resp = mvc.perform(post("/order/api/v1/orders")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-SubId", "sub")
                        .header("X-User-UserName", "login")
                        .header("X-User-FirstName", URLEncoder.encode("Иван", UTF_8))
                        .header("X-User-LastName", URLEncoder.encode("Иванов", UTF_8))
                        .header("X-User-Email", "ivan@example.com")
                        .header("X-User-Role", "USER")
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(resp).get("orderUuid").asText());
    }

    @Test
    @DisplayName("Без JWT доступ к /order/api/v1/** запрещён (401), swagger и health открыты")
    void orderEndpoints_unauthenticated_401() throws Exception {
        CompositeRequest cr = new CompositeRequest(
                List.of(new OrderRequest(UUID.randomUUID(), 1)),
                new AddressDto("City", "Street", 1, 1));

        mvc.perform(post("/order/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cr)))
                .andExpect(status().isUnauthorized());

        then(shopController).should(never()).checkOrder(any());
    }

    @Test
    @DisplayName("Создание заказа → 201 + корректный JSON, без Kafka‑события")
    void createOrder_onlyHttp() throws Exception {
        UUID articleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<OrderRequest> reqs = List.of(new OrderRequest(articleId, 3));
        given(shopController.checkOrder(reqs)).willReturn(true);
        given(shopController.getSumTotalPriceOrder(reqs)).willReturn(BigDecimal.valueOf(250.5));

        CompositeRequest payload = new CompositeRequest(
                reqs,
                new AddressDto("City", "Street", 10, 101)
        );
        String body = objectMapper.writeValueAsString(payload);

        mvc.perform(post("/order/api/v1/orders")
                        .with(jwt())
                        .contentType("application/json")
                        .header("X-User-SubId", "sub")
                        .header("X-User-UserName", "login")
                        .header("X-User-FirstName", URLEncoder.encode("Иван", UTF_8))
                        .header("X-User-LastName", URLEncoder.encode("Иванов", UTF_8))
                        .header("X-User-Email", "ivan@example.com")
                        .header("X-User-Role", "USER")
                        .content(body)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.totalAmount").value(250.5))
                .andExpect(jsonPath("$.orderStatus").value("WAITING_FOR_PAYMENT"));
    }

    private record KafkaEntry(String key, String value) {
    }

    // Вычитываем все записи topic-orders одноразовым консьюмером (своя group + earliest),
    // чтобы проверить события, опубликованные outbox-релеем после pay/refund
    private List<KafkaEntry> readAllFromTopic() {
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );

        try (Consumer<String, String> consumer =
                     new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
            consumer.subscribe(List.of("topic-orders"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            List<KafkaEntry> entries = new ArrayList<>();
            records.forEach(record -> entries.add(new KafkaEntry(record.key(), record.value())));
            return entries;
        }
    }

    @Test
    @DisplayName("Оплата заказа → 200 + SUCCESS; событие через outbox в Kafka с ключом orderUuid")
    void payOrder_andKafka() throws Exception {
        BigDecimal total = new BigDecimal("250.50");
        UUID orderUuid = createOrder(total);

        PaymentRequest payReq = new PaymentRequest(orderUuid, "1111 2222 3333 4444");
        AccountRequest acct = new AccountRequest(orderUuid, total.setScale(2), payReq.getCardNumber());
        UUID payId = UUID.randomUUID();
        given(paymentController.paymentOrder(acct))
                .willReturn(new PaymentResponse(payId, Status.SUCCESS));

        String payJson = objectMapper.writeValueAsString(payReq);
        mvc.perform(post("/order/api/v1/orders/{uuid}/payments", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findAll().getFirst().getOrderUuid()).isEqualTo(orderUuid);

        outboxRelay.pollOnce();

        assertThat(outboxRepository.count()).isZero();

        List<KafkaEntry> entries = readAllFromTopic();
        assertThat(entries)
                .anySatisfy(entry -> {
                    assertThat(entry.key()).isEqualTo(orderUuid.toString());
                    assertThat(entry.value()).contains("\"orderStatus\":\"SUCCESS\"");
                });
    }

    @Test
    @DisplayName("Двойная оплата → вторая отклоняется с 400, повторного вызова payment нет")
    void payOrder_twice_secondRejected() throws Exception {
        BigDecimal total = new BigDecimal("250.50");
        UUID orderUuid = createOrder(total);

        PaymentRequest payReq = new PaymentRequest(orderUuid, "1111 2222 3333 4444");
        AccountRequest acct = new AccountRequest(orderUuid, total.setScale(2), payReq.getCardNumber());
        given(paymentController.paymentOrder(acct))
                .willReturn(new PaymentResponse(UUID.randomUUID(), Status.SUCCESS));

        mvc.perform(post("/order/api/v1/orders/{uuid}/payments", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        mvc.perform(post("/order/api/v1/orders/{uuid}/payments", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageError").value("Заказ уже оплачен!"));

        org.mockito.Mockito.verify(paymentController).paymentOrder(acct);
    }

    @Test
    @DisplayName("Идемпотентное создание: повторная попытка с тем же orderUuid не создаёт дубль; повторное списание — идемпотентный no-op на стороне shop")
    void createOrder_sameUuid_idempotentReplay() throws Exception {
        UUID articleId = UUID.fromString("00000000-0000-0000-0000-000000000009");
        List<OrderRequest> reqs = List.of(new OrderRequest(articleId, 2));
        UUID orderUuid = UUID.randomUUID();

        given(shopController.checkOrder(reqs)).willReturn(true);
        given(shopController.getSumTotalPriceOrder(reqs)).willReturn(BigDecimal.valueOf(200.0));

        User user = User.builder().id(1L).username("login").subId("sub").build();

        OrderDto first = orderService.createOrder(user, orderUuid, reqs, new AddressDto("City", "Street", 1, 1));
        OrderDto second = orderService.createOrder(user, orderUuid, reqs, new AddressDto("City", "Street", 1, 1));

        assertThat(first.orderUuid()).isEqualTo(orderUuid);
        assertThat(second.orderUuid()).isEqualTo(orderUuid);

        org.mockito.Mockito.verify(shopController, org.mockito.Mockito.times(2))
                .changeTotalQuantityProductsAfterCreateOrder(
                        org.mockito.ArgumentMatchers.argThat(scr -> scr != null && orderUuid.equals(scr.getOrderUuid())));
    }

    @Test
    @DisplayName("Создание заказа с отрицательным количеством → 400 (валидация)")
    void createOrder_negativeQuantity_rejected() throws Exception {
        UUID articleId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        List<OrderRequest> reqs = List.of(new OrderRequest(articleId, -5));

        CompositeRequest cr = new CompositeRequest(reqs, new AddressDto("City", "Street", 10, 101));

        mvc.perform(post("/order/api/v1/orders")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-SubId", "sub")
                        .header("X-User-UserName", "login")
                        .header("X-User-FirstName", URLEncoder.encode("Иван", UTF_8))
                        .header("X-User-LastName", URLEncoder.encode("Иванов", UTF_8))
                        .header("X-User-Email", "ivan@example.com")
                        .header("X-User-Role", "USER")
                        .content(objectMapper.writeValueAsString(cr)))
                .andExpect(status().isBadRequest());

        then(shopController).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Создание заказа с пустым списком товаров → 400 (валидация)")
    void createOrder_emptyRequests_rejected() throws Exception {
        CompositeRequest cr = new CompositeRequest(List.of(), new AddressDto("City", "Street", 10, 101));

        mvc.perform(post("/order/api/v1/orders")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-SubId", "sub")
                        .header("X-User-UserName", "login")
                        .header("X-User-FirstName", URLEncoder.encode("Иван", UTF_8))
                        .header("X-User-LastName", URLEncoder.encode("Иванов", UTF_8))
                        .header("X-User-Email", "ivan@example.com")
                        .header("X-User-Role", "USER")
                        .content(objectMapper.writeValueAsString(cr)))
                .andExpect(status().isBadRequest());

        then(shopController).should(never()).checkOrder(any());
    }

    @Test
    @DisplayName("Отмена заказа → 200 + REFUNDED + Kafka‑сообщение")
    void refundedOrder_andKafka() throws Exception {
        BigDecimal total = new BigDecimal("300.00");
        UUID orderUuid = createOrder(total);

        PaymentRequest payReq = new PaymentRequest(orderUuid, "1111 2222 3333 4444");
        AccountRequest acctPay = new AccountRequest(orderUuid, total.setScale(2), payReq.getCardNumber());
        UUID payId = UUID.randomUUID();
        given(paymentController.paymentOrder(acctPay))
                .willReturn(new PaymentResponse(payId, Status.SUCCESS));

        mvc.perform(post("/order/api/v1/orders/{uuid}/payments", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        PaymentRequest refundReq = new PaymentRequest(orderUuid, "9999 8888 7777 6666");
        AccountRequest acctRefund = new AccountRequest(orderUuid, total.setScale(2), refundReq.getCardNumber());
        UUID refundId = UUID.randomUUID();
        given(paymentController.refundedOrder(acctRefund))
                .willReturn(new PaymentResponse(refundId, Status.REFUNDED));

        mvc.perform(post("/order/api/v1/orders/{uuid}/refunds", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "sub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("Заказ успешно отменен"));

        outboxRelay.pollOnce();

        List<KafkaEntry> entries = readAllFromTopic();
        assertThat(entries)
                .anySatisfy(entry -> {
                    assertThat(entry.key()).isEqualTo(orderUuid.toString());
                    assertThat(entry.value()).contains("\"orderStatus\":\"REFUNDED\"");
                });
    }

    @Test
    @DisplayName("IDOR: чужой X-User-SubId не может оплатить заказ — 403, статус не меняется")
    void payOrder_foreignSubId_forbidden() throws Exception {
        UUID orderUuid = createOrder(new BigDecimal("150.00"));

        PaymentRequest payReq = new PaymentRequest(orderUuid, "1111 2222 3333 4444");

        mvc.perform(post("/order/api/v1/orders/{uuid}/payments", orderUuid)
                        .with(jwt())
                        .header("X-User-SubId", "someone-else")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.messageError").value("Ошибка: заказ принадлежит другому пользователю"));

        then(paymentController).should(never()).paymentOrder(any());
    }
}
