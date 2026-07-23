package com.anish.url_shortener.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        Boolean emailVerified,
        LocalDateTime createdAt,
        long totalUrls
) {
}
