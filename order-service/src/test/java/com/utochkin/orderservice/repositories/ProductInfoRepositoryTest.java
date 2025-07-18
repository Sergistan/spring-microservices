package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.Order;
import com.utochkin.orderservice.models.ProductInfo;
import com.utochkin.orderservice.models.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = OrderRepository.class
        )
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@ActiveProfiles("test")
public class ProductInfoRepositoryTest {

    @Autowired
    private OrderRepository orderRepo;
    @Autowired
    private ProductInfoRepository productInfoRepository;

    @Test
    @DisplayName("Сохраняем ProductInfo и проверяем связь к Order")
    void saveProductInfosForOrder() {
        Order order = Order.builder()
                .orderUuid(UUID.randomUUID())
                .totalAmount(50.0)
                .orderStatus(Status.WAITING_FOR_PAYMENT)
                .createdAt(LocalDateTime.now())
                .build();
        Order savedOrder = orderRepo.save(order);

        ProductInfo productInfo = new ProductInfo();
        productInfo.setArticleId(UUID.randomUUID());
        productInfo.setQuantity(2);
        productInfo.setOrder(savedOrder);

        ProductInfo productInfo2 = new ProductInfo();
        productInfo2.setArticleId(UUID.randomUUID());
        productInfo2.setQuantity(5);
        productInfo2.setOrder(savedOrder);

        productInfoRepository.saveAll(List.of(productInfo, productInfo2));

        List<ProductInfo> list = productInfoRepository.findAll();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getOrder().getOrderUuid()).isEqualTo(savedOrder.getOrderUuid());
    }

}
