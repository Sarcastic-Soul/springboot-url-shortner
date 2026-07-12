package com.anish.url_shortener.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType
) {}
