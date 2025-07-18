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
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(
        includeFilters = @Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = AccountRepository.class
        )
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@ActiveProfiles("test")
public class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager em;

    private Account persistAccount(String cardNumber, double amount) {
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
        Account saved = persistAccount(card, 250.0);

        Optional<Account> maybe = accountRepository.findAccountByCardNumber(card);

        assertThat(maybe).isPresent()
                .get()
                .extracting(Account::getAmountMoney, Account::getCardNumber)
                .containsExactly(250.0, card);
    }

    @Test
    @DisplayName("findAccountByCardNumber → несуществующий номер возвращает empty")
    void findAccountByCardNumber_nonExisting_returnsEmpty() {
        persistAccount("5555 6666 7777 8888", 75.0);

        Optional<Account> maybe = accountRepository.findAccountByCardNumber("0000 0000 0000 0000");

        assertThat(maybe).isEmpty();
    }

}
