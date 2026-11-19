package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.Address;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = OrderRepository.class
        )
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@ActiveProfiles("test")
public class AddressRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private AddressRepository addressRepo;

    @Test
    @DisplayName("Сохраняем адрес и получаем по ID")
    void saveAndFind() {
        Address addr = new Address();
        addr.setCity("Moscow");
        addr.setStreet("Tverskaya");
        addr.setHouseNumber(10);
        addr.setApartmentNumber(5);

        Address saved = addressRepo.save(addr);

        Address found = addressRepo.findById(saved.getId()).orElseThrow();
        assertThat(found.getCity()).isEqualTo("Moscow");
        assertThat(found.getHouseNumber()).isEqualTo(10);
    }

}
