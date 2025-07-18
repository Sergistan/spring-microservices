package com.utochkin.orderservice.repositories;

import com.utochkin.orderservice.models.Role;
import com.utochkin.orderservice.models.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

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
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepo;

    @Test
    @DisplayName("existsBySubIdAndUsername корректно определяет существование")
    void existsBySubIdAndUsername() {
        User u = User.builder()
                .subId("sub123")
                .username("jdoe")
                .firstName("John")
                .lastName("Doe")
                .email("jdoe@example.com")
                .role(Role.USER)
                .build();
        userRepo.save(u);

        boolean exists = userRepo.existsBySubIdAndUsername("sub123", "jdoe");
        assertThat(exists).isTrue();

        assertThat(userRepo.existsBySubIdAndUsername("sub123", "someone")).isFalse();
    }

    @Test
    @DisplayName("findBySubIdAndUsername возвращает Optional<User>")
    void findBySubIdAndUsername() {
        User u = User.builder()
                .subId("subABC")
                .username("alice")
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@example.com")
                .role(Role.ADMIN)
                .build();
        userRepo.save(u);

        Optional<User> found = userRepo.findBySubIdAndUsername("subABC", "alice");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

}
