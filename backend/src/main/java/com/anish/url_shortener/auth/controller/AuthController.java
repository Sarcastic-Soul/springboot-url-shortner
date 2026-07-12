package com.anish.url_shortener.auth.controller;

import com.anish.url_shortener.auth.dto.AuthResponse;
import com.anish.url_shortener.auth.dto.LoginRequest;
import com.anish.url_shortener.auth.dto.RegisterRequest;
import com.anish.url_shortener.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
         System.out.println("REGISTER ENDPOINT HIT");
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request
    ){
        return authService.login(request);
    }
}
