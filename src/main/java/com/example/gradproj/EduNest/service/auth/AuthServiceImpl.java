package com.example.gradproj.EduNest.service.auth;

import com.example.gradproj.EduNest.dto.auth.RegisterRequest;
import com.example.gradproj.EduNest.entity.User;
import com.example.gradproj.EduNest.exception.EmailAlreadyUsedException;
import com.example.gradproj.EduNest.repository.UserRepository;
import com.example.gradproj.EduNest.service.AuthService;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(UserRepository userRepository,PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User register(RegisterRequest registerRequest) {
       String emailLower = registerRequest.getEmail().toLowerCase().trim();
       if(userRepository.existsByEmail(emailLower)){
          throw new EmailAlreadyUsedException("Email is already in use");
}
User.Role role;
        try {
            role = User.Role.valueOf(registerRequest.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role. Allowed: STUDENT, MENTOR");
        }
        User user = User.builder()
                .firstName(registerRequest.getFirstName())
                .lastName(registerRequest.getLastName())
                .email(emailLower)
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .role(role)
                .build();
        return userRepository.save(user);
    }
}
