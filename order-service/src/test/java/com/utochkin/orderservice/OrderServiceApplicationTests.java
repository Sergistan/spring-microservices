package com.utochkin.orderservice;

import com.utochkin.orderservice.repositories.OrderRepository;
import com.utochkin.orderservice.repositories.ProductInfoRepository;
import com.utochkin.orderservice.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest(
        properties = {
                "spring.profiles.active=test",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration,"  +
                        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                "spring.cloud.compatibility-verifier.enabled=false"
        }
)
public class OrderServiceApplicationTests {

    @TestConfiguration
    static class SecurityTestConfig {

        @Bean
        public ProductInfoRepository mockProductInfoRepository() {
            return Mockito.mock(ProductInfoRepository.class);
        }

        @Bean
        public UserRepository mockUserRepository() {
            return Mockito.mock(UserRepository.class);
        }

        @Bean
        public OrderRepository mockOrderRepository() {
            return Mockito.mock(OrderRepository.class);
        }

        @Bean
        public JwtDecoder jwtDecoder() {
            return Mockito.mock(JwtDecoder.class);
        }
    }

    @Test
    void contextLoads() {
    }

}
