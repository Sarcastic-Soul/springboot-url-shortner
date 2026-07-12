package com.anish.url_shortener.url.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UrlSummaryResponse(

        UUID id,

        String shortCode,

        String shortUrl,

        String originalUrl,

        String title,

        Long clickCount,

        Boolean active,

        LocalDateTime createdAt,

        LocalDateTime expiresAt

) {}
