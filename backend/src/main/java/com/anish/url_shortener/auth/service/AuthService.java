package com.anish.url_shortener.auth.service;

import com.anish.url_shortener.auth.dto.AuthResponse;
import com.anish.url_shortener.auth.dto.LoginRequest;
import com.anish.url_shortener.auth.dto.RegisterRequest;
import com.anish.url_shortener.auth.entity.User;
import com.anish.url_shortener.auth.repository.UserRepository;
import com.anish.url_shortener.security.JwtService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {

        if(userRepository.existsByEmailIgnoreCase(request.email()))
            throw new IllegalArgumentException("Email already exists");

        User user = User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .emailVerified(false)
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail());

        return new AuthResponse(
                token,
                "Bearer"
        );
    }

    public AuthResponse login(LoginRequest request){

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid credentials"));

        if(!passwordEncoder.matches(request.password(), user.getPasswordHash())){
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getEmail());

        return new AuthResponse(
                token,
                "Bearer"
        );
    }
}
