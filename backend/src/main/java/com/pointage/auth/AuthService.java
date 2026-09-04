package com.pointage.auth;

import com.pointage.user.User;
import com.pointage.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.asAdmin() ? User.Role.ADMIN : User.Role.USER);
        if (request.team() != null) {
            user.setTeam(User.Team.valueOf(request.team().toUpperCase()));
        }
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}
