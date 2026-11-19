package com.utochkin.paymentservice.repositories;
import com.utochkin.paymentservice.models.Account;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;

@DataJpaTest(
        includeFilters = @Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = AccountRepository.class
        )
)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public class AccountRepositoryTest {

    // Реальный PostgreSQL в docker-контейнере: @ServiceConnection подставляет реквизиты
    // dataSource в тестовый контекст (вместо встроенной H2; схема создаётся Hibernate)
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager em;

    private Account persistAccount(String cardNumber, BigDecimal amount) {
        Account account = Account.builder()
                .cardNumber(cardNumber)
                .amountMoney(amount)
                .build();
        em.persist(account);
        em.flush();
        return account;
    }


    @Test
    @DisplayName("findAccountByCardNumber → существующий номер возвращает Optional с сущностью")
    void findAccountByCardNumber_existing_returnsEntity() {
        String card = "4444 3333 2222 1111";
        Account saved = persistAccount(card, BigDecimal.valueOf(250.0));

        Optional<Account> maybe = accountRepository.findAccountByCardNumber(card);

        assertThat(maybe).isPresent()
                .get()
                .extracting(Account::getAmountMoney, Account::getCardNumber)
                .containsExactly(BigDecimal.valueOf(250.0), card);
    }

    @Test
    @DisplayName("findAccountByCardNumber → несуществующий номер возвращает empty")
    void findAccountByCardNumber_nonExisting_returnsEmpty() {
        persistAccount("5555 6666 7777 8888", BigDecimal.valueOf(75.0));

        Optional<Account> maybe = accountRepository.findAccountByCardNumber("0000 0000 0000 0000");

        assertThat(maybe).isEmpty();
    }

    @Test
    @DisplayName("withdraw → при достаточном балансе атомарно уменьшает и возвращает 1")
    void withdraw_sufficient_updatesBalance() {
        persistAccount("4444 3333 2222 1111", BigDecimal.valueOf(250.0));

        int updated = accountRepository.withdraw("4444 3333 2222 1111", BigDecimal.valueOf(100.0));
        em.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(accountRepository.findAccountByCardNumber("4444 3333 2222 1111"))
                .get()
                .extracting(Account::getAmountMoney)
                .isEqualTo(BigDecimal.valueOf(150.0).setScale(2));
    }

    @Test
    @DisplayName("withdraw → при недостаточном балансе не меняет баланс и возвращает 0")
    void withdraw_insufficient_returnsZero() {
        persistAccount("4444 3333 2222 1111", BigDecimal.valueOf(50.0));

        int updated = accountRepository.withdraw("4444 3333 2222 1111", BigDecimal.valueOf(100.0));

        assertThat(updated).isZero();
        em.clear();
        assertThat(accountRepository.findAccountByCardNumber("4444 3333 2222 1111"))
                .get()
                .extracting(Account::getAmountMoney)
                .isEqualTo(BigDecimal.valueOf(50.0).setScale(2));
    }

    @Test
    @DisplayName("withdraw → несуществующая карта возвращает 0")
    void withdraw_unknownCard_returnsZero() {
        persistAccount("4444 3333 2222 1111", BigDecimal.valueOf(50.0));

        assertThat(accountRepository.withdraw("0000 0000 0000 0000", BigDecimal.valueOf(10.0))).isZero();
    }

    @Test
    @DisplayName("deposit → атомарно увеличивает баланс и возвращает 1")
    void deposit_increasesBalance() {
        persistAccount("4444 3333 2222 1111", BigDecimal.valueOf(250.0));

        int updated = accountRepository.deposit("4444 3333 2222 1111", BigDecimal.valueOf(75.0));
        em.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(accountRepository.findAccountByCardNumber("4444 3333 2222 1111"))
                .get()
                .extracting(Account::getAmountMoney)
                .isEqualTo(BigDecimal.valueOf(325.0).setScale(2));
    }

}
