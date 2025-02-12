package com.utochkin.orderservice.services;

import com.utochkin.orderservice.exceptions.UserNotFoundException;
import com.utochkin.orderservice.models.Role;
import com.utochkin.orderservice.models.User;
import com.utochkin.orderservice.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User findUserBySubIdAndUsername(String subId, String username) {
        return userRepository.findBySubIdAndUsername(subId, username).orElseThrow(UserNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public boolean isUserExistsByUsername(String subId, String username) {
        return userRepository.existsBySubIdAndUsername(subId, username);
    }

    @Transactional
    public User createUser(String subId, String username, String firstName, String lastName, String email, String role) {
        User user = User.builder()
                .subId(subId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .role(Role.valueOf(role))
                .build();

        return userRepository.save(user);
    }
}
