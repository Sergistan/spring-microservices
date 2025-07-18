import com.fasterxml.jackson.databind.ObjectMapper;
import com.utochkin.orderservice.OrderServiceApplication;
import com.utochkin.orderservice.controllers.PaymentController;
import com.utochkin.orderservice.controllers.ShopController;
import com.utochkin.orderservice.dto.AddressDto;
import com.utochkin.orderservice.models.Status;
import com.utochkin.orderservice.request.*;
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
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = OrderServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.cloud.compatibility-verifier.enabled=false",
                "spring.liquibase.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
                "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {"topic-orders"}
)
@EnableAutoConfiguration(exclude = {CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class})
class OrderServiceIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private ShopController shopController;
    @MockitoBean
    private PaymentController paymentController;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UUID createOrder(double total) throws Exception {
        UUID articleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<OrderRequest> req = List.of(new OrderRequest(articleId, 3));
        given(shopController.checkOrder(req)).willReturn(true);
        given(shopController.getSumTotalPriceOrder(req)).willReturn(total);

        CompositeRequest cr = new CompositeRequest(req, new AddressDto("City", "Street", 10, 101));
        String json = objectMapper.writeValueAsString(cr);

        String resp = mvc.perform(post("/order/api/v1/create")
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
    @DisplayName("Создание заказа → 201 + корректный JSON, без Kafka‑события")
    void createOrder_onlyHttp() throws Exception {
        UUID articleId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        List<OrderRequest> reqs = List.of(new OrderRequest(articleId, 3));
        given(shopController.checkOrder(reqs)).willReturn(true);
        given(shopController.getSumTotalPriceOrder(reqs)).willReturn(250.5);

        CompositeRequest payload = new CompositeRequest(
                reqs,
                new AddressDto("City", "Street", 10, 101)
        );
        String body = objectMapper.writeValueAsString(payload);

        mvc.perform(post("/order/api/v1/create")
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

    @Test
    @DisplayName("Оплата заказа → 200 + SUCCESS + Kafka‑сообщение")
    void payOrder_andKafka() throws Exception {
        double total = 250.5;
        UUID orderUuid = createOrder(total);

        PaymentRequest payReq = new PaymentRequest(orderUuid, "1111 2222 3333 4444");
        AccountRequest acct = new AccountRequest(total, payReq.getCardNumber());
        UUID payId = UUID.randomUUID();
        given(paymentController.paymentOrder(acct))
                .willReturn(new PaymentResponse(payId, Status.SUCCESS));

        String payJson = objectMapper.writeValueAsString(payReq);
        mvc.perform(post("/order/api/v1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        kafkaTemplate.flush();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                UUID.randomUUID().toString(), "false", embeddedKafka
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        try (org.apache.kafka.clients.consumer.Consumer<String, String> consumer =
                     new DefaultKafkaConsumerFactory<String, String>(consumerProps)
                             .createConsumer()) {

            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "topic-orders");

            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer);
            List<String> values = new ArrayList<>();
            records.forEach(record -> values.add(record.value()));

            assertThat(values)
                    .anyMatch(v -> v.contains("\"orderStatus\":\"SUCCESS\""));
        }
    }

    @Test
    @DisplayName("Отмена заказа → 200 + REFUNDED + Kafka‑сообщение")
    void refundedOrder_andKafka() throws Exception {
        double total = 300.0;
        UUID orderUuid = createOrder(total);

        PaymentRequest payReq = new PaymentRequest(orderUuid, "1111 2222 3333 4444");
        AccountRequest acctPay = new AccountRequest(total, payReq.getCardNumber());
        UUID payId = UUID.randomUUID();
        given(paymentController.paymentOrder(acctPay))
                .willReturn(new PaymentResponse(payId, Status.SUCCESS));

        mvc.perform(post("/order/api/v1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        PaymentRequest refundReq = new PaymentRequest(orderUuid, "9999 8888 7777 6666");
        AccountRequest acctRefund = new AccountRequest(total, refundReq.getCardNumber());
        UUID refundId = UUID.randomUUID();
        given(paymentController.refundedOrder(acctRefund))
                .willReturn(new PaymentResponse(refundId, Status.REFUNDED));

        mvc.perform(post("/order/api/v1/refunded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refundReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("Заказ успешно отменен"));

        kafkaTemplate.flush();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(UUID.randomUUID().toString(), "false", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "topic-orders");

            ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer);
            List<String> values = new ArrayList<>();
            records.forEach(record -> values.add(record.value()));

            assertThat(values).anyMatch(v -> v.contains("\"orderStatus\":\"REFUNDED\""));
        }
    }
}