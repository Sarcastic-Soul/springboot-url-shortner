package com.anish.url_shortener.url.dto;

import java.time.LocalDateTime;

public record CachedRedirectEntry(
        String originalUrl,
        LocalDateTime expiresAt
) {
}
