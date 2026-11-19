package com.utochkin.orderservice.services;

import com.utochkin.orderservice.exceptions.UserNotFoundException;
import com.utochkin.orderservice.models.Role;
import com.utochkin.orderservice.models.User;
import com.utochkin.orderservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Локальный справочник пользователей order-service: создаётся/ищется по subId+username из
 * заголовков X-User-* при первом заказе. Некорректная роль из заголовка безопасно
 * приводится к USER (parseRole) — сервисные токены не содержат realm-ролей.
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class UserService {

    private final UserRepository userRepository;

    /** Поиск локального пользователя по паре subId+username (Keycloak sub + preferred_username). */
    @Transactional(readOnly = true)
    public User findUserBySubIdAndUsername(String subId, String username) {
        log.info("UserService: поиск пользователя subId={} username={}", subId, username);
        return userRepository.findBySubIdAndUsername(subId, username).orElseThrow(UserNotFoundException::new);
    }

    /** Существует ли локальный пользователь (первый заказ -> false -> будет создан). */
    @Transactional(readOnly = true)
    public boolean isUserExistsByUsername(String subId, String username) {
        boolean isUserExists = userRepository.existsBySubIdAndUsername(subId, username);
        log.info("UserService: пользователь {} {}", username, isUserExists ? "существует" : "не существует");
        return isUserExists;
    }

    /** Создание локальной записи пользователя из заголовков X-User-* (первый заказ). */
    @Transactional
    public User createUser(String subId, String username, String firstName, String lastName, String email, String role) {
        log.info("UserService: создаём пользователя {} ({})", username, subId);
        User user = User.builder()
                .subId(subId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .role(parseRole(role))
                .build();

        User savedUser = userRepository.save(user);

        log.info("UserService: пользователь {} создан", savedUser.getUsername());

        return savedUser;
    }

    /** Безопасный разбор роли: некорректное/отсутствующее значение (сервисные токены) -> USER. */
    private Role parseRole(String role) {
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("UserService: некорректная роль '{}' — используем USER по умолчанию", role);
            return Role.USER;
        }
    }
}
