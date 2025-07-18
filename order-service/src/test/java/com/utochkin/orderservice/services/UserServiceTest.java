package com.utochkin.orderservice.services;

import com.utochkin.orderservice.exceptions.UserNotFoundException;
import com.utochkin.orderservice.models.Role;
import com.utochkin.orderservice.models.User;
import com.utochkin.orderservice.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private final String subId = "sub123";
    private final String username = "alice";

    private User sampleUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sampleUser = User.builder()
                .id(1L)
                .subId(subId)
                .username(username)
                .firstName("Alice")
                .lastName("Wonder")
                .email("alice@example.com")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("isUserExistsByUsername → true когда существует")
    void isUserExistsByUsername_WhenExists() {
        given(userRepository.existsBySubIdAndUsername(subId, username)).willReturn(true);

        boolean exists = userService.isUserExistsByUsername(subId, username);
        assertThat(exists).isTrue();
        then(userRepository).should().existsBySubIdAndUsername(subId, username);
    }

    @Test
    @DisplayName("isUserExistsByUsername → false когда не существует")
    void isUserExistsByUsername_WhenNotExists() {
        given(userRepository.existsBySubIdAndUsername(subId, username)).willReturn(true);

        boolean exists = userService.isUserExistsByUsername(subId + 1, username);
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("findUserBySubIdAndUsername → возвращает пользователя")
    void findUserBySubIdAndUsername_WhenPresent() {
        given(userRepository.findBySubIdAndUsername(subId, username)).willReturn(Optional.of(sampleUser));

        User user = userService.findUserBySubIdAndUsername(subId, username);
        assertThat(user).isEqualTo(sampleUser);
    }

    @Test
    @DisplayName("findUserBySubIdAndUsername → выбрасывает исключение, когда не найден")
    void findUserBySubIdAndUsername_WhenMissing() {
        given(userRepository.findBySubIdAndUsername(subId, username)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findUserBySubIdAndUsername(subId, username)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("createUser → сохраняет и возвращает нового пользователя")
    void createUser_ShouldSaveAndReturn() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        given(userRepository.save(captor.capture()))
                .willAnswer(inv -> {
                    User toSave = inv.getArgument(0);
                    toSave.setId(99L);
                    return toSave;
                });

        User created = userService.createUser(subId, username, "Alice", "Wonder", "alice@e.com", "USER");

        assertThat(captor.getValue().getSubId()).isEqualTo(subId);
        assertThat(captor.getValue().getUsername()).isEqualTo(username);
        assertThat(created.getId()).isEqualTo(99L);
    }
}
